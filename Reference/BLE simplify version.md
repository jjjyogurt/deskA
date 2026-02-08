


#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>

#define SERVICE_UUID        "c1f0d8a0-8b1b-4c0b-9e1c-2c1f7f2b2c11"
#define CHARACTERISTIC_UUID "c1f0d8a1-8b1b-4c0b-9e1c-2c1f7f2b2c11"

const int PIN_UP = 17;
const int PIN_DOWN = 18;

// Helper to handle the logic: HIGH = Released, LOW = Pressed
void handleButtons(uint8_t cmd) {
  if (cmd == 0x01) { // UP
    digitalWrite(PIN_UP, LOW);    // Press Up
    digitalWrite(PIN_DOWN, HIGH); // Release Down
    Serial.println("STATE: UP Pressed (LOW)");
  } 
  else if (cmd == 0x02) { // DOWN
    digitalWrite(PIN_UP, HIGH);   // Release Up
    digitalWrite(PIN_DOWN, LOW);  // Press Down
    Serial.println("STATE: DOWN Pressed (LOW)");
  } 
  else { // STOP / IDLE
    digitalWrite(PIN_UP, HIGH);   // Release both
    digitalWrite(PIN_DOWN, HIGH);
    Serial.println("STATE: Both Released (HIGH)");
  }
}

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) { Serial.println(">>> Connected"); }
    void onDisconnect(BLEServer* pServer) {
      handleButtons(0x00); // Safety: release buttons if phone disconnects
      BLEDevice::startAdvertising();
      Serial.println(">>> Disconnected - Buttons Released");
    }
};

class MyCharacteristicCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pChar) {
      String val = pChar->getValue();
      if (val.length() > 0) handleButtons((uint8_t)val[0]);
    }
};

void setup() {
  Serial.begin(115200);

  // Set pins to HIGH BEFORE defining as OUTPUT to prevent a "fake press" on boot
  digitalWrite(PIN_UP, HIGH);
  digitalWrite(PIN_DOWN, HIGH);
  pinMode(PIN_UP, OUTPUT);
  pinMode(PIN_DOWN, OUTPUT);

  BLEDevice::init("Desk_Sim_ActiveLow");
  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);
  BLECharacteristic *pChar = pService->createCharacteristic(
                               CHARACTERISTIC_UUID,
                               BLECharacteristic::PROPERTY_WRITE
                             );

  pChar->setCallbacks(new MyCharacteristicCallbacks());
  pService->start();

  BLEDevice::getAdvertising()->addServiceUUID(SERVICE_UUID);
  BLEDevice::startAdvertising();
  Serial.println("Active Low Simulator Ready (Default HIGH)");
}

void loop() { delay(10); }