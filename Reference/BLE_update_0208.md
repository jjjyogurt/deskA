#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>

// UUIDs from your desk_ble_config.json
#define SERVICE_UUID        "c1f0d8a0-8b1b-4c0b-9e1c-2c1f7f2b2c11"
#define CHARACTERISTIC_UUID "c1f0d8a1-8b1b-4c0b-9e1c-2c1f7f2b2c11"

// Motor control pins (update if your wiring differs)
constexpr int PIN_INA = 17;
constexpr int PIN_INB = 18;

enum class MotionState { Idle, MovingUp, MovingDown };

MotionState currentState = MotionState::Idle;

void applyState(MotionState next) {
  currentState = next;
  switch (currentState) {
    case MotionState::MovingUp:
      digitalWrite(PIN_INA, HIGH);
      digitalWrite(PIN_INB, HIGH);
      break;
    case MotionState::MovingDown:
      digitalWrite(PIN_INA, LOW);
      digitalWrite(PIN_INB, LOW);
      break;
    case MotionState::Idle:
    default:
      digitalWrite(PIN_INA, LOW);
      digitalWrite(PIN_INB, HIGH);
      break;
  }
}

// Server callbacks to monitor connection status
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      Serial.println(">>> App Connected");
    };

    void onDisconnect(BLEServer* pServer) {
      Serial.println(">>> App Disconnected");
      applyState(MotionState::Idle);
      // Restart advertising so the app can find it again
      BLEDevice::startAdvertising();
      Serial.println(">>> Advertising restarted...");
    }
};

// Characteristic callbacks to handle incoming commands
class MyCharacteristicCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      // Fixed: Using String instead of std::string for compatibility with ESP32 library v3.0+
      String value = pCharacteristic->getValue();

      if (value.length() > 0) {
        Serial.print("--- Command Received (Hex): ");
        for (int i = 0; i < value.length(); i++) {
          Serial.printf("%02x ", (uint8_t)value[i]);
        }
        Serial.println();

        // Logic based on hex values in your desk_ble_config.json
        uint8_t command = (uint8_t)value[0];
        
        switch (command) {
          case 0x01: // "up": "01"
            Serial.println("ACTION: Moving Desk UP");
            applyState(MotionState::MovingUp);
            break;
            
          case 0x02: // "down": "02"
            Serial.println("ACTION: Moving Desk DOWN");
            applyState(MotionState::MovingDown);
            break;

          case 0x00: // "stop": "00"
            Serial.println("ACTION: Stop Desk");
            applyState(MotionState::Idle);
            break;

          case 0x11: // "memory1": "11"
            Serial.println("ACTION: Memory Slot 1");
            break;

          case 0x12: // "memory2": "12"
            Serial.println("ACTION: Memory Slot 2");
            break;

          case 0x13: // "memory3": "13"
            Serial.println("ACTION: Memory Slot 3");
            break;
            
          default:
            Serial.printf("ACTION: Unknown Command (0x%02x)\n", command);
            break;
        }
      }
    }
};

void setup() {
  Serial.begin(115200);
  Serial.println("Initializing ESP32S3 Actuator...");

  pinMode(PIN_INA, OUTPUT);
  pinMode(PIN_INB, OUTPUT);
  applyState(MotionState::Idle);

  // Create the BLE Device
  BLEDevice::init("ESP32S3_ACTUATOR");

  // Create the BLE Server
  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  // Create the BLE Service
  BLEService *pService = pServer->createService(SERVICE_UUID);

  // Create the Command Characteristic
  BLECharacteristic *pCharacteristic = pService->createCharacteristic(
                                         CHARACTERISTIC_UUID,
                                         BLECharacteristic::PROPERTY_READ |
                                         BLECharacteristic::PROPERTY_WRITE |
                                         BLECharacteristic::PROPERTY_NOTIFY
                                       );

  // Attach callbacks to handle incoming commands from the Android app
  pCharacteristic->setCallbacks(new MyCharacteristicCallbacks());

  // Start the service
  pService->start();

  // Start advertising so the Android app can discover this device
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  
  // These help with iPhone/Android discovery connection stability
  pAdvertising->setMinPreferred(0x06);  
  pAdvertising->setMinPreferred(0x12);
  
  BLEDevice::startAdvertising();
  
  Serial.println("BLE Actuator is ready! Waiting for connection...");
}

void loop() {
  // BLE is event-driven via callbacks, so nothing is needed here.
  delay(10);
}