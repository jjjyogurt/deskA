#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEScan.h>
#include <BLEAdvertisedDevice.h>
#include <BLERemoteCharacteristic.h>
#include <BLEClient.h>

// Must match Android bridge service/characteristic UUIDs
static BLEUUID SERVICE_UUID("c1f0d8a0-8b1b-4c0b-9e1c-2c1f7f2b2c11");
static BLEUUID CHARACTERISTIC_UUID("c1f0d8a1-8b1b-4c0b-9e1c-2c1f7f2b2c11");

// RECOMMENDED: lock to Android bridge MAC to avoid connecting to Desk ESP32 directly.
// Example: "AA:BB:CC:DD:EE:FF"
// Leave empty ONLY for testing (service UUID match only, can connect to wrong device).
static const char* TARGET_BRIDGE_ADDRESS = "";

// One button on ESP32-S3 pin 7
constexpr int BUTTON_PIN = 7;
constexpr uint8_t BUTTON_ACTIVE_LEVEL = LOW;    // button to GND
constexpr uint8_t BUTTON_INACTIVE_LEVEL = HIGH; // INPUT_PULLUP

constexpr unsigned long DEBOUNCE_MS = 30;
constexpr unsigned long MULTI_CLICK_GAP_MS = 300;
constexpr unsigned long RESCAN_INTERVAL_MS = 2000;
constexpr unsigned long LOOP_DELAY_MS = 5;

enum class MotionIntent { Idle, Up, Down };

struct RemoteState {
  MotionIntent intent;
};

RemoteState remoteState{MotionIntent::Idle};

// BLE globals
BLEScan* bleScan = nullptr;
BLEClient* bleClient = nullptr;
BLERemoteCharacteristic* cmdChar = nullptr;
BLEAddress* foundAddress = nullptr;

bool shouldConnect = false;
bool isConnected = false;

// Button globals
uint8_t lastRawButton = BUTTON_INACTIVE_LEVEL;
uint8_t stableButton = BUTTON_INACTIVE_LEVEL;
unsigned long lastDebounceMs = 0;
unsigned long firstClickMs = 0;
uint8_t clickCount = 0;

// Scan globals
unsigned long lastScanAttemptMs = 0;

// ---------- Helpers ----------
bool hasTargetAddress() {
  return TARGET_BRIDGE_ADDRESS != nullptr && strlen(TARGET_BRIDGE_ADDRESS) > 0;
}

bool addressMatchesTarget(const BLEAddress& address) {
  if (!hasTargetAddress()) return true;
  String seen = String(address.toString().c_str());
  String target = String(TARGET_BRIDGE_ADDRESS);
  seen.toLowerCase();
  target.toLowerCase();
  return seen == target;
}

void clearFoundAddress() {
  if (foundAddress != nullptr) {
    delete foundAddress;
    foundAddress = nullptr;
  }
}

class BridgeAdvertisedCallbacks : public BLEAdvertisedDeviceCallbacks {
  void onResult(BLEAdvertisedDevice advertisedDevice) override {
    if (!advertisedDevice.haveServiceUUID()) return;
    if (!advertisedDevice.isAdvertisingService(SERVICE_UUID)) return;

    BLEAddress addr = advertisedDevice.getAddress();
    if (!addressMatchesTarget(addr)) {
      return;
    }

    Serial.print("Found Android bridge: ");
    Serial.println(addr.toString().c_str());

    clearFoundAddress();
    foundAddress = new BLEAddress(addr);
    shouldConnect = true;
    BLEDevice::getScan()->stop();
  }
};

class BridgeClientCallbacks : public BLEClientCallbacks {
  void onConnect(BLEClient* pclient) override {
    Serial.println("BLE connected to Android bridge");
  }

  void onDisconnect(BLEClient* pclient) override {
    Serial.println("BLE disconnected from Android bridge");
    isConnected = false;
    cmdChar = nullptr;
  }
};

bool connectToBridge() {
  if (foundAddress == nullptr) return false;

  if (bleClient == nullptr) {
    bleClient = BLEDevice::createClient();
    bleClient->setClientCallbacks(new BridgeClientCallbacks());
  }

  Serial.print("Connecting to ");
  Serial.println(foundAddress->toString().c_str());

  if (!bleClient->connect(*foundAddress)) {
    Serial.println("Connect failed");
    return false;
  }

  BLERemoteService* remoteService = bleClient->getService(SERVICE_UUID);
  if (remoteService == nullptr) {
    Serial.println("Service not found");
    bleClient->disconnect();
    return false;
  }

  BLERemoteCharacteristic* remoteChar = remoteService->getCharacteristic(CHARACTERISTIC_UUID);
  if (remoteChar == nullptr) {
    Serial.println("Characteristic not found");
    bleClient->disconnect();
    return false;
  }

  cmdChar = remoteChar;
  isConnected = true;
  Serial.println("Bridge command characteristic ready");
  return true;
}

bool sendBridgeCommand(uint8_t command) {
  if (!isConnected || cmdChar == nullptr) {
    Serial.println("Not connected, command not sent");
    return false;
  }

  uint8_t payload[1] = {command};
  try {
    // false = write without response
    cmdChar->writeValue(payload, 1, false);
    Serial.printf("Sent command: 0x%02X\n", command);
    return true;
  } catch (...) {
    Serial.println("Write failed");
    isConnected = false;
    cmdChar = nullptr;
    return false;
  }
}

RemoteState nextStateAfterSinglePress(const RemoteState& current) {
  if (current.intent == MotionIntent::Up) {
    sendBridgeCommand(0x00); // stop
    return RemoteState{MotionIntent::Idle};
  }
  sendBridgeCommand(0x01); // up
  return RemoteState{MotionIntent::Up};
}

RemoteState nextStateAfterDoublePress(const RemoteState& current) {
  if (current.intent == MotionIntent::Down) {
    sendBridgeCommand(0x00); // stop
    return RemoteState{MotionIntent::Idle};
  }
  sendBridgeCommand(0x02); // down
  return RemoteState{MotionIntent::Down};
}

void processButton() {
  const unsigned long now = millis();
  const uint8_t raw = digitalRead(BUTTON_PIN);

  if (raw != lastRawButton) {
    lastDebounceMs = now;
    lastRawButton = raw;
  }

  if ((now - lastDebounceMs) > DEBOUNCE_MS && raw != stableButton) {
    stableButton = raw;

    // Count click on release
    if (stableButton == BUTTON_INACTIVE_LEVEL) {
      clickCount++;
      if (clickCount == 1) {
        firstClickMs = now;
      }
    }
  }

  if (clickCount > 0 && (now - firstClickMs) > MULTI_CLICK_GAP_MS) {
    if (clickCount == 1) {
      remoteState = nextStateAfterSinglePress(remoteState);
    } else {
      remoteState = nextStateAfterDoublePress(remoteState);
    }
    clickCount = 0;
    firstClickMs = 0;
  }
}

void ensureConnection() {
  if (isConnected) return;

  if (shouldConnect) {
    shouldConnect = false;
    if (!connectToBridge()) {
      // keep scanning cycle alive
      clearFoundAddress();
    }
    return;
  }

  const unsigned long now = millis();
  if (now - lastScanAttemptMs >= RESCAN_INTERVAL_MS) {
    lastScanAttemptMs = now;
    Serial.println("Scanning for Android bridge...");
    bleScan->start(2, false); // short blocking scan
  }
}

void setup() {
  Serial.begin(115200);
  Serial.println("ESP32-S3 remote sender booting...");

  if (hasTargetAddress()) {
    Serial.print("Target bridge MAC: ");
    Serial.println(TARGET_BRIDGE_ADDRESS);
  } else {
    Serial.println("No target MAC set (service-only match mode)");
  }

  pinMode(BUTTON_PIN, INPUT_PULLUP);
  lastRawButton = digitalRead(BUTTON_PIN);
  stableButton = lastRawButton;

  BLEDevice::init("ESP32S3_REMOTE");

  bleScan = BLEDevice::getScan();
  bleScan->setAdvertisedDeviceCallbacks(new BridgeAdvertisedCallbacks());
  bleScan->setInterval(1349);
  bleScan->setWindow(449);
  bleScan->setActiveScan(true);

  Serial.println("Ready. Waiting for Android bridge advertisement...");
}

void loop() {
  processButton();
  ensureConnection();
  delay(LOOP_DELAY_MS);
}