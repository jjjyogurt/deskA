#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLE2902.h>

// Must match Android-side UUIDs
static BLEUUID SERVICE_UUID("c1f0d8a0-8b1b-4c0b-9e1c-2c1f7f2b2c11");
static BLEUUID CHARACTERISTIC_UUID("c1f0d8a1-8b1b-4c0b-9e1c-2c1f7f2b2c11");

// Requested device name
static const char *DEVICE_NAME = "ESP32S3_Remote_Desk";

// One button on ESP32-S3 pin 7
constexpr int BUTTON_PIN = 7;
constexpr uint8_t BUTTON_ACTIVE_LEVEL = LOW;    // button to GND
constexpr uint8_t BUTTON_INACTIVE_LEVEL = HIGH; // INPUT_PULLUP

constexpr unsigned long DEBOUNCE_MS = 30;
constexpr unsigned long MULTI_CLICK_GAP_MS = 300;
constexpr unsigned long LOOP_DELAY_MS = 5;

// Debug options (temporary)
constexpr bool DEBUG_BUTTON = true;
constexpr unsigned long PIN_DEBUG_INTERVAL_MS = 200;

// Command bytes
constexpr uint8_t CMD_STOP = 0x00;
constexpr uint8_t CMD_UP = 0x01;
constexpr uint8_t CMD_DOWN = 0x02;

enum class MotionIntent { Idle, Up, Down };

// BLE globals
BLEServer *bleServer = nullptr;
BLECharacteristic *cmdChar = nullptr;
bool centralConnected = false;

// Button globals
uint8_t lastRawButton = BUTTON_INACTIVE_LEVEL;
uint8_t stableButton = BUTTON_INACTIVE_LEVEL;
unsigned long lastDebounceMs = 0;
unsigned long firstClickMs = 0;
uint8_t clickCount = 0;

// Debug globals
unsigned long lastPinDebugMs = 0;

// Motion state
MotionIntent intentState = MotionIntent::Idle;

const char *commandName(uint8_t command) {
  switch (command) {
    case CMD_STOP: return "STOP";
    case CMD_UP: return "UP";
    case CMD_DOWN: return "DOWN";
    default: return "UNKNOWN";
  }
}

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *pServer) override {
    centralConnected = true;
    Serial.println("[BLE] Android connected");
  }

  void onDisconnect(BLEServer *pServer) override {
    centralConnected = false;
    Serial.println("[BLE] Android disconnected, restarting advertising...");
    BLEDevice::startAdvertising();
  }
};

class CommandWriteCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharacteristic) override {
    String value = pCharacteristic->getValue();
    if (value.length() == 0) {
      Serial.println("[BLE] Received empty write");
      return;
    }

    uint8_t cmd = static_cast<uint8_t>(value[0]);
    Serial.printf("[BLE] Received write: 0x%02X (%s)\n", cmd, commandName(cmd));
  }
};

void publishCommand(uint8_t command) {
  if (cmdChar == nullptr) {
    Serial.println("[BLE] Characteristic unavailable");
    return;
  }

  cmdChar->setValue(&command, 1);
  cmdChar->notify();

  Serial.printf("[BLE] Published command: 0x%02X (%s), connected=%s\n",
                command, commandName(command), centralConnected ? "YES" : "NO");
}

void applySinglePress() {
  Serial.println("[BTN] Single press detected");

  if (intentState == MotionIntent::Up) {
    publishCommand(CMD_STOP);
    intentState = MotionIntent::Idle;
    return;
  }

  publishCommand(CMD_UP);
  intentState = MotionIntent::Up;
}

void applyDoublePress() {
  Serial.println("[BTN] Double press detected");

  if (intentState == MotionIntent::Down) {
    publishCommand(CMD_STOP);
    intentState = MotionIntent::Idle;
    return;
  }

  publishCommand(CMD_DOWN);
  intentState = MotionIntent::Down;
}

void processButton() {
  const unsigned long now = millis();
  const uint8_t raw = digitalRead(BUTTON_PIN);

  if (raw != lastRawButton) {
    if (DEBUG_BUTTON) {
      Serial.printf("[DBG] Raw edge: %u -> %u at %lu ms\n", lastRawButton, raw, now);
    }
    lastDebounceMs = now;
    lastRawButton = raw;
  }

  if ((now - lastDebounceMs) > DEBOUNCE_MS && raw != stableButton) {
    if (DEBUG_BUTTON) {
      Serial.printf("[DBG] Stable change: %u -> %u at %lu ms\n", stableButton, raw, now);
    }

    stableButton = raw;

    // Count click on release
    if (stableButton == BUTTON_INACTIVE_LEVEL) {
      clickCount++;
      if (clickCount == 1) {
        firstClickMs = now;
      }
      if (DEBUG_BUTTON) {
        Serial.printf("[DBG] Release counted, clickCount=%u, firstClickMs=%lu\n",
                      clickCount, firstClickMs);
      }
    }
  }

  if (clickCount > 0 && (now - firstClickMs) > MULTI_CLICK_GAP_MS) {
    if (DEBUG_BUTTON) {
      Serial.printf("[DBG] Click window elapsed, clickCount=%u\n", clickCount);
    }

    if (clickCount == 1) {
      applySinglePress();
    } else {
      applyDoublePress();
    }
    clickCount = 0;
    firstClickMs = 0;
  }
}

void setupBlePeripheral() {
  BLEDevice::init(DEVICE_NAME);

  bleServer = BLEDevice::createServer();
  bleServer->setCallbacks(new ServerCallbacks());

  BLEService *service = bleServer->createService(SERVICE_UUID);

  cmdChar = service->createCharacteristic(
      CHARACTERISTIC_UUID,
      BLECharacteristic::PROPERTY_READ |
      BLECharacteristic::PROPERTY_WRITE |
      BLECharacteristic::PROPERTY_WRITE_NR |
      BLECharacteristic::PROPERTY_NOTIFY);

  cmdChar->addDescriptor(new BLE2902());
  cmdChar->setCallbacks(new CommandWriteCallbacks());

  uint8_t initial = CMD_STOP;
  cmdChar->setValue(&initial, 1);

  service->start();

  BLEAdvertising *advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);

  BLEAdvertisementData scanResponseData;
  scanResponseData.setName(DEVICE_NAME);
  advertising->setScanResponseData(scanResponseData);

  advertising->setScanResponse(true);
  advertising->setMinPreferred(0x06);
  advertising->setMinPreferred(0x12);

  BLEDevice::startAdvertising();
  Serial.println("[BLE] Peripheral advertising started");
  Serial.print("[BLE] Device name: ");
  Serial.println(DEVICE_NAME);
}

void setup() {
  Serial.begin(115200);
  Serial.println("ESP32-S3 remote peripheral booting...");
  Serial.printf("[CFG] BUTTON_PIN=%d active=%d inactive=%d\n",
                BUTTON_PIN, BUTTON_ACTIVE_LEVEL, BUTTON_INACTIVE_LEVEL);

  pinMode(BUTTON_PIN, INPUT_PULLUP);
  lastRawButton = digitalRead(BUTTON_PIN);
  stableButton = lastRawButton;

  Serial.printf("[CFG] Initial button raw=%u stable=%u\n", lastRawButton, stableButton);

  setupBlePeripheral();

  Serial.println("Ready. Single press=UP/STOP, double press=DOWN/STOP");
}

void loop() {
  processButton();

  if (DEBUG_BUTTON) {
    const unsigned long now = millis();
    if ((now - lastPinDebugMs) >= PIN_DEBUG_INTERVAL_MS) {
      lastPinDebugMs = now;
      Serial.printf("[PIN] raw=%u stable=%u click=%u connected=%s\n",
                    digitalRead(BUTTON_PIN),
                    stableButton,
                    clickCount,
                    centralConnected ? "YES" : "NO");
    }
  }

  delay(LOOP_DELAY_MS);
}