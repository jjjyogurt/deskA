This is the sample code for my ESP32 board: 

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEUtils.h>

constexpr int PIN_INA = 17;
constexpr int PIN_INB = 18;

constexpr char SERVICE_UUID[] = "c1f0d8a0-8b1b-4c0b-9e1c-2c1f7f2b2c11";
constexpr char RX_UUID[]      = "c1f0d8a1-8b1b-4c0b-9e1c-2c1f7f2b2c11";
constexpr char TX_UUID[]      = "c1f0d8a2-8b1b-4c0b-9e1c-2c1f7f2b2c11";

enum class MotionState { Idle, MovingUp, MovingDown, Stopped, EmergencyStop };

BLECharacteristic* txChar = nullptr;
MotionState state = MotionState::Idle;

void applyState(MotionState next) {
  state = next;
  switch (state) {
    case MotionState::MovingUp:
      digitalWrite(PIN_INA, HIGH);
      digitalWrite(PIN_INB, HIGH);
      break;
    case MotionState::MovingDown:
      digitalWrite(PIN_INA, LOW);
      digitalWrite(PIN_INB, LOW);
      break;
    default:
      digitalWrite(PIN_INA, LOW);
      digitalWrite(PIN_INB, HIGH);
      break;
  }
}

const char* stateStr() {
  switch (state) {
    case MotionState::Idle: return "idle";
    case MotionState::MovingUp: return "moving_up";
    case MotionState::MovingDown: return "moving_down";
    case MotionState::Stopped: return "stopped";
    case MotionState::EmergencyStop: return "emergency_stop";
    default: return "unknown";
  }
}

void notifyStatus(const char* err) {
  if (!txChar) return;
  String payload = String("{\"state\":\"") + stateStr() + "\",\"pos\":0,\"error\":";
  payload += (err ? String("\"") + err + "\"" : "null");
  payload += "}";
  txChar->setValue(payload.c_str());
  txChar->notify();
}

class RxCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* ch) override {
    String v = ch->getValue();
    if (v.indexOf("up") != -1) {
      applyState(MotionState::MovingUp);
      notifyStatus(nullptr);
    } else if (v.indexOf("down") != -1) {
      applyState(MotionState::MovingDown);
      notifyStatus(nullptr);
    } else if (v.indexOf("stop") != -1) {
      applyState(MotionState::Stopped);
      notifyStatus(nullptr);
    } else if (v.indexOf("status") != -1) {
      notifyStatus(nullptr);
    } else {
      notifyStatus("invalid_cmd");
    }
  }
};

void setup() {
  Serial.begin(115200);
  pinMode(PIN_INA, OUTPUT);
  pinMode(PIN_INB, OUTPUT);
  applyState(MotionState::Stopped);

  BLEDevice::init("ESP32S3_ACTUATOR");
  BLEServer* server = BLEDevice::createServer();
  BLEService* service = server->createService(SERVICE_UUID);

  BLECharacteristic* rxChar = service->createCharacteristic(
    RX_UUID, BLECharacteristic::PROPERTY_WRITE);
  txChar = service->createCharacteristic(
    TX_UUID, BLECharacteristic::PROPERTY_NOTIFY);

  rxChar->setCallbacks(new RxCallbacks());
  service->start();

  BLEAdvertising* adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(SERVICE_UUID);
  adv->start();
}

void loop() {
  delay(10);
}