#include <Arduino.h>
#include <esp_display_panel.hpp>
#include <lvgl.h>
#include "lvgl_v8_port.h"
#include <ESP_Knob.h>
#include <ui.h>

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// ---------------- Pin mapping ----------------
#define GPIO_NUM_KNOB_PIN_A 7
#define GPIO_NUM_KNOB_PIN_B 6
#define GPIO_BUTTON_PIN     GPIO_NUM_9 // boot/button line on this board

// ---------------- BLE profile ----------------
static const char *BLE_DEVICE_NAME = "ESP32C3_PEBBLE";
static const char *REMOTE_SERVICE_UUID = "f17a1000-8b1b-4c0b-9e1c-2c1f7f2b2c11";
static const char *REMOTE_EVENT_UUID   = "f17a1001-8b1b-4c0b-9e1c-2c1f7f2b2c11";

enum RemoteEvent : uint8_t {
  EVT_STOP      = 0x00,
  EVT_UP_START  = 0x01,
  EVT_DOWN_START= 0x02
};

BLECharacteristic *gEventChar = nullptr;
bool gBleConnected = false;

// ---------------- Gesture timing ----------------
static const uint32_t DEBOUNCE_MS = 30;
static const uint32_t HOLD_MS = 220;
static const uint32_t DOUBLE_CLICK_GAP_MS = 260;

enum HoldMode {
  HOLD_NONE,
  HOLD_UP,
  HOLD_DOWN
};

// Button FSM state
bool rawPressed = false;
bool stablePressed = false;
bool lastRawPressed = false;
uint32_t lastDebounceMs = 0;
uint32_t pressStartMs = 0;
uint32_t firstReleaseMs = 0;
uint8_t clickCount = 0;
bool holdActive = false;
HoldMode holdMode = HOLD_NONE;

// ---------------- Optional: knob UI callbacks ----------------
ESP_Knob *knob = nullptr;
void onKnobLeftEventCallback(int count, void *usr_data) {
  Serial.printf("Knob left, count=%d\n", count);
  lvgl_port_lock(-1);
  LVGL_knob_event((void *)KNOB_LEFT);
  lvgl_port_unlock();
}
void onKnobRightEventCallback(int count, void *usr_data) {
  Serial.printf("Knob right, count=%d\n", count);
  lvgl_port_lock(-1);
  LVGL_knob_event((void *)KNOB_RIGHT);
  lvgl_port_unlock();
}

// ---------------- BLE helpers ----------------
class RemoteServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *pServer) override {
    gBleConnected = true;
    Serial.println("BLE connected");
  }

  void onDisconnect(BLEServer *pServer) override {
    gBleConnected = false;
    Serial.println("BLE disconnected");
    BLEDevice::startAdvertising();
  }
};

static void notifyRemoteEvent(RemoteEvent evt) {
  if (!gEventChar || !gBleConnected) return;
  uint8_t payload[1] = { static_cast<uint8_t>(evt) };
  gEventChar->setValue(payload, 1);
  gEventChar->notify();
  Serial.printf("BLE notify event: 0x%02X\n", payload[0]);
}

// ---------------- Gesture logic ----------------
static inline bool isPressedRaw() {
  // Button on IO9 is typically active-low
  return digitalRead(GPIO_BUTTON_PIN) == LOW;
}

static void resetGestureState() {
  clickCount = 0;
  holdActive = false;
  holdMode = HOLD_NONE;
}

static void processButtonFSM() {
  uint32_t now = millis();

  rawPressed = isPressedRaw();
  if (rawPressed != lastRawPressed) {
    lastDebounceMs = now;
    lastRawPressed = rawPressed;
  }

  if ((now - lastDebounceMs) > DEBOUNCE_MS) {
    if (stablePressed != rawPressed) {
      stablePressed = rawPressed;

      if (stablePressed) {
        // press edge
        if (clickCount == 1 && (now - firstReleaseMs) <= DOUBLE_CLICK_GAP_MS) {
          clickCount = 2; // second press
        } else if (clickCount == 0 || (now - firstReleaseMs) > DOUBLE_CLICK_GAP_MS) {
          clickCount = 1; // first press
        }
        pressStartMs = now;
      } else {
        // release edge
        if (holdActive) {
          notifyRemoteEvent(EVT_STOP);
          resetGestureState();
        } else {
          firstReleaseMs = now;
        }
      }
    }
  }

  // Hold detection
  if (stablePressed && !holdActive && clickCount > 0) {
    if ((now - pressStartMs) >= HOLD_MS) {
      if (clickCount == 1) {
        holdMode = HOLD_UP;
        holdActive = true;
        notifyRemoteEvent(EVT_UP_START);
      } else if (clickCount == 2) {
        holdMode = HOLD_DOWN;
        holdActive = true;
        notifyRemoteEvent(EVT_DOWN_START);
      }
    }
  }

  // Timeout for single/double click window when no hold started
  if (!stablePressed && !holdActive && clickCount > 0) {
    if ((now - firstReleaseMs) > DOUBLE_CLICK_GAP_MS) {
      resetGestureState();
    }
  }
}

using namespace esp_panel::drivers;
using namespace esp_panel::board;

void setup() {
  Serial.begin(115200);
  delay(100);

  // ---- IO init
  pinMode(GPIO_BUTTON_PIN, INPUT_PULLUP);

  // ---- Display/LVGL init (from your reference code)
  Serial.println("Initializing board");
  Board *board = new Board();
  board->init();
  assert(board->begin());

  Serial.println("Initializing LVGL");
  lvgl_port_init(board->getLCD(), board->getTouch());

  Serial.println("Initialize Knob device");
  knob = new ESP_Knob(GPIO_NUM_KNOB_PIN_A, GPIO_NUM_KNOB_PIN_B);
  knob->begin();
  knob->attachLeftEventCallback(onKnobLeftEventCallback);
  knob->attachRightEventCallback(onKnobRightEventCallback);

  Serial.println("Create UI");
  lvgl_port_lock(-1);
  ui_init();
  lvgl_port_unlock();

  // ---- BLE init
  Serial.println("Initializing BLE");
  BLEDevice::init(BLE_DEVICE_NAME);
  BLEServer *server = BLEDevice::createServer();
  server->setCallbacks(new RemoteServerCallbacks());

  BLEService *service = server->createService(REMOTE_SERVICE_UUID);
  gEventChar = service->createCharacteristic(
      REMOTE_EVENT_UUID,
      BLECharacteristic::PROPERTY_NOTIFY | BLECharacteristic::PROPERTY_READ
  );
  gEventChar->addDescriptor(new BLE2902());
  uint8_t initVal = EVT_STOP;
  gEventChar->setValue(&initVal, 1);

  service->start();
  BLEAdvertising *adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(REMOTE_SERVICE_UUID);
  adv->setScanResponse(true);
  BLEDevice::startAdvertising();

  Serial.println("Ready: single-hold=UP, double-hold=DOWN, release=STOP");
}

void loop() {
  processButtonFSM();
  delay(5);
}