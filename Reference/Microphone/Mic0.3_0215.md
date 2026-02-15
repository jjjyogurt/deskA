#include <Arduino.h>
#include <BLE2902.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <driver/i2s.h>

// increase max PCM payload ntu 256; bytrstbspeed to 244 ~100kb/s
// Must match Android-side UUIDs
static BLEUUID SERVICE_UUID("c1f0d8a0-8b1b-4c0b-9e1c-2c1f7f2b2c11");
static BLEUUID CHARACTERISTIC_UUID("c1f0d8a1-8b1b-4c0b-9e1c-2c1f7f2b2c11");
static BLEUUID AUDIO_CHARACTERISTIC_UUID("c1f0d8a2-8b1b-4c0b-9e1c-2c1f7f2b2c11");

// Device name
static const char *DEVICE_NAME = "ESP32S3_Remote_Desk";

// Desk control button (keep existing behavior)
constexpr int BUTTON_PIN = 7;
constexpr uint8_t BUTTON_ACTIVE_LEVEL = LOW;    // button to GND
constexpr uint8_t BUTTON_INACTIVE_LEVEL = HIGH; // INPUT_PULLUP

// Mic toggle button
constexpr int MIC_BUTTON_PIN = 12;

// INMP441 I2S pins
constexpr int I2S_WS_PIN = 16;
constexpr int I2S_SCK_PIN = 6;
constexpr int I2S_SD_PIN = 18;

// Timing
constexpr unsigned long DEBOUNCE_MS = 30;
constexpr unsigned long MULTI_CLICK_GAP_MS = 300;
constexpr unsigned long LOOP_DELAY_MS = 2;

// Debug
constexpr bool DEBUG_BUTTON = false;
constexpr bool DEBUG_MIC = false;
constexpr unsigned long PIN_DEBUG_INTERVAL_MS = 200;

// Commands
constexpr uint8_t CMD_STOP = 0x00;
constexpr uint8_t CMD_UP = 0x01;
constexpr uint8_t CMD_DOWN = 0x02;
constexpr uint8_t CMD_MIC_START = 0x10;
constexpr uint8_t CMD_MIC_STOP = 0x11;

// Mic settings (ASR-compatible)
constexpr uint32_t MIC_SAMPLE_RATE = 16000;
constexpr uint8_t MIC_CHANNELS = 1;
constexpr uint8_t MIC_BITS_PER_SAMPLE = 16;
constexpr unsigned long MIC_MAX_RECORDING_MS = 60000; // 1 minute
constexpr size_t MIC_MAX_PCM_BYTES = MIC_SAMPLE_RATE * MIC_CHANNELS * (MIC_BITS_PER_SAMPLE / 8) * 60;

// Audio packet format:
// [0..1] seq (uint16 LE), [2] flags (bit0=last), [3] reserved, [4..] PCM payload.
// Tuned for MTU=256 links: 4-byte header + 244-byte payload.
constexpr size_t AUDIO_PACKET_HEADER_BYTES = 4;
constexpr size_t AUDIO_PACKET_PAYLOAD_BYTES = 244;
constexpr size_t AUDIO_PACKET_TOTAL_BYTES = AUDIO_PACKET_HEADER_BYTES + AUDIO_PACKET_PAYLOAD_BYTES; // 248
constexpr uint8_t AUDIO_FLAG_LAST = 0x01;
constexpr int AUDIO_PACKETS_PER_LOOP = 1;
constexpr uint8_t LAST_PACKET_REDUNDANCY = 5;

enum class MotionIntent { Idle, Up, Down };

// BLE globals
BLEServer *bleServer = nullptr;
BLECharacteristic *cmdChar = nullptr;
BLECharacteristic *audioChar = nullptr;
bool centralConnected = false;

// Desk button globals
uint8_t lastRawButton = BUTTON_INACTIVE_LEVEL;
uint8_t stableButton = BUTTON_INACTIVE_LEVEL;
unsigned long lastDebounceMs = 0;
unsigned long firstClickMs = 0;
uint8_t clickCount = 0;

// Mic button globals
uint8_t lastRawMicButton = BUTTON_INACTIVE_LEVEL;
uint8_t stableMicButton = BUTTON_INACTIVE_LEVEL;
unsigned long lastMicDebounceMs = 0;

// Debug globals
unsigned long lastPinDebugMs = 0;

// Motion state
MotionIntent intentState = MotionIntent::Idle;

// Mic state
uint8_t *micPcmBuffer = nullptr;
size_t micBufferedBytes = 0;
bool micRecording = false;
bool micTransferPending = false;
bool micTransferZeroOnly = false;
size_t micTransferOffset = 0;
uint16_t micTransferSeq = 0;
unsigned long micRecordingStartMs = 0;
unsigned long micTransferStartMs = 0;
uint32_t micSessionId = 0;
uint32_t micTransferPacketsSent = 0;

// I2S temp buffer
constexpr size_t I2S_READ_SAMPLES = 256;
int32_t i2sReadBuffer[I2S_READ_SAMPLES];

// Reused packet buffer avoids large stack allocations
static uint8_t audioPacketBuffer[AUDIO_PACKET_TOTAL_BYTES];

const char *commandName(uint8_t command) {
  switch (command) {
  case CMD_STOP:
    return "STOP";
  case CMD_UP:
    return "UP";
  case CMD_DOWN:
    return "DOWN";
  case CMD_MIC_START:
    return "MIC_START";
  case CMD_MIC_STOP:
    return "MIC_STOP";
  default:
    return "UNKNOWN";
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
    Serial.println("[BLE] Command characteristic unavailable");
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

// Desk control logic unchanged.
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
        Serial.printf("[DBG] Release counted, clickCount=%u, firstClickMs=%lu\n", clickCount, firstClickMs);
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

bool setupI2sInput() {
  i2s_config_t config = {};
  config.mode = static_cast<i2s_mode_t>(I2S_MODE_MASTER | I2S_MODE_RX);
  config.sample_rate = MIC_SAMPLE_RATE;
  config.bits_per_sample = I2S_BITS_PER_SAMPLE_32BIT; // read 32-bit, downscale to 16-bit
  config.channel_format = I2S_CHANNEL_FMT_ONLY_LEFT;  // INMP441 L/R pin expected LOW for left
  config.communication_format = I2S_COMM_FORMAT_STAND_I2S;
  config.intr_alloc_flags = ESP_INTR_FLAG_LEVEL1;
  config.dma_buf_count = 8;
  config.dma_buf_len = 128;
  config.use_apll = false;
  config.tx_desc_auto_clear = false;
  config.fixed_mclk = 0;

  i2s_pin_config_t pinConfig = {};
  pinConfig.bck_io_num = I2S_SCK_PIN;
  pinConfig.ws_io_num = I2S_WS_PIN;
  pinConfig.data_out_num = I2S_PIN_NO_CHANGE;
  pinConfig.data_in_num = I2S_SD_PIN;

  esp_err_t installErr = i2s_driver_install(I2S_NUM_0, &config, 0, nullptr);
  if (installErr != ESP_OK) {
    Serial.printf("[MIC] i2s_driver_install failed err=%d\n", installErr);
    return false;
  }

  esp_err_t pinErr = i2s_set_pin(I2S_NUM_0, &pinConfig);
  if (pinErr != ESP_OK) {
    Serial.printf("[MIC] i2s_set_pin failed err=%d\n", pinErr);
    return false;
  }

  i2s_zero_dma_buffer(I2S_NUM_0);
  Serial.println("[MIC] I2S initialized (INMP441)");
  return true;
}

bool ensureMicBuffer() {
  if (micPcmBuffer != nullptr) {
    return true;
  }

  Serial.printf("[MEM] psramFound=%s freePsram=%u freeHeap=%u need=%u\n",
                psramFound() ? "YES" : "NO",
                static_cast<unsigned>(ESP.getFreePsram()),
                static_cast<unsigned>(ESP.getFreeHeap()),
                static_cast<unsigned>(MIC_MAX_PCM_BYTES));

  if (psramFound()) {
    micPcmBuffer = static_cast<uint8_t *>(ps_malloc(MIC_MAX_PCM_BYTES));
    Serial.printf("[MIC] PSRAM detected, trying buffer=%u bytes\n", static_cast<unsigned>(MIC_MAX_PCM_BYTES));
  } else {
    Serial.println("[MIC] PSRAM not found, trying DRAM allocation (may fail)");
    micPcmBuffer = static_cast<uint8_t *>(malloc(MIC_MAX_PCM_BYTES));
  }

  if (micPcmBuffer == nullptr) {
    Serial.println("[MIC] Failed to allocate 1-minute PCM buffer");
    return false;
  }

  Serial.println("[MIC] PCM buffer allocated");
  return true;
}

void startMicRecording() {
  if (micRecording) return;
  if (micTransferPending) {
    Serial.println("[MIC] Transfer in progress; ignore start tap");
    return;
  }
  if (!ensureMicBuffer()) {
    Serial.println("[MIC] Cannot start: no buffer");
    return;
  }

  micSessionId++;
  micBufferedBytes = 0;
  micRecording = true;
  micRecordingStartMs = millis();

  publishCommand(CMD_MIC_START);
  Serial.printf("[MIC] START session=%lu\n", static_cast<unsigned long>(micSessionId));
}

void prepareMicTransfer() {
  micTransferPending = true;
  micTransferOffset = 0;
  micTransferSeq = 0;
  micTransferPacketsSent = 0;
  micTransferStartMs = millis();
  micTransferZeroOnly = (micBufferedBytes == 0);

  Serial.printf("[MIC] TRANSFER_PREP session=%lu bytes=%u zeroOnly=%s\n",
                static_cast<unsigned long>(micSessionId),
                static_cast<unsigned>(micBufferedBytes),
                micTransferZeroOnly ? "YES" : "NO");
}

void stopMicRecording(const char *reason) {
  if (!micRecording) return;

  micRecording = false;
  const unsigned long durationMs = millis() - micRecordingStartMs;

  publishCommand(CMD_MIC_STOP);
  Serial.printf("[MIC] STOP session=%lu reason=%s durationMs=%lu bytes=%u\n",
                static_cast<unsigned long>(micSessionId),
                reason,
                durationMs,
                static_cast<unsigned>(micBufferedBytes));

  prepareMicTransfer();
}

void handleMicTap() {
  if (!micRecording) {
    startMicRecording();
  } else {
    stopMicRecording("BUTTON_TAP");
  }
}

void processMicButton() {
  const unsigned long now = millis();
  const uint8_t raw = digitalRead(MIC_BUTTON_PIN);

  if (raw != lastRawMicButton) {
    lastMicDebounceMs = now;
    lastRawMicButton = raw;
  }

  if ((now - lastMicDebounceMs) > DEBOUNCE_MS && raw != stableMicButton) {
    stableMicButton = raw;

    // trigger on release edge
    if (stableMicButton == BUTTON_INACTIVE_LEVEL) {
      if (DEBUG_MIC) Serial.printf("[MIC] Tap release at %lu ms\n", now);
      handleMicTap();
    }
  }
}

inline void appendSample16ToBuffer(int16_t sample) {
  if (micBufferedBytes + 2 > MIC_MAX_PCM_BYTES) return;
  micPcmBuffer[micBufferedBytes++] = static_cast<uint8_t>(sample & 0xFF);        // LE low
  micPcmBuffer[micBufferedBytes++] = static_cast<uint8_t>((sample >> 8) & 0xFF); // LE high
}

void captureMicIfRecording() {
  if (!micRecording) return;

  const unsigned long now = millis();
  if ((now - micRecordingStartMs) >= MIC_MAX_RECORDING_MS) {
    stopMicRecording("MAX_60S");
    return;
  }

  size_t bytesRead = 0;
  esp_err_t err = i2s_read(I2S_NUM_0, i2sReadBuffer, sizeof(i2sReadBuffer), &bytesRead, 0);
  if (err != ESP_OK) {
    if (DEBUG_MIC) Serial.printf("[MIC] i2s_read err=%d\n", err);
    return;
  }
  if (bytesRead == 0) return;

  const size_t samplesRead = bytesRead / sizeof(int32_t);
  for (size_t i = 0; i < samplesRead; i++) {
    if (micBufferedBytes + 2 > MIC_MAX_PCM_BYTES) {
      stopMicRecording("BUFFER_FULL");
      return;
    }
    int32_t s32 = i2sReadBuffer[i];
    int16_t s16 = static_cast<int16_t>(s32 >> 14); // 32-bit to 16-bit
    appendSample16ToBuffer(s16);
  }
}

void notifyAudioPacket(const uint8_t *payload, uint16_t payloadLen, bool isLast) {
  if (audioChar == nullptr) {
    Serial.println("[MIC] audioChar unavailable");
    return;
  }

  if (payloadLen > AUDIO_PACKET_PAYLOAD_BYTES) {
    payloadLen = static_cast<uint16_t>(AUDIO_PACKET_PAYLOAD_BYTES);
  }

  audioPacketBuffer[0] = static_cast<uint8_t>(micTransferSeq & 0xFF);
  audioPacketBuffer[1] = static_cast<uint8_t>((micTransferSeq >> 8) & 0xFF);
  audioPacketBuffer[2] = isLast ? AUDIO_FLAG_LAST : 0x00;
  audioPacketBuffer[3] = 0x00;

  if (payloadLen > 0 && payload != nullptr) {
    memcpy(audioPacketBuffer + AUDIO_PACKET_HEADER_BYTES, payload, payloadLen);
  }

  audioChar->setValue(audioPacketBuffer, AUDIO_PACKET_HEADER_BYTES + payloadLen);
  audioChar->notify();

  micTransferPacketsSent++;
  micTransferSeq++;

  if (DEBUG_MIC && (micTransferPacketsSent % 200 == 0 || isLast)) {
    Serial.printf("[MIC] TX packet=%lu seq=%u payload=%u last=%s\n",
                  static_cast<unsigned long>(micTransferPacketsSent),
                  static_cast<unsigned>(micTransferSeq - 1),
                  static_cast<unsigned>(payloadLen),
                  isLast ? "YES" : "NO");
  }
}

void sendLastPacketWithRedundancy(const uint8_t *payload, uint16_t payloadLen) {
  for (uint8_t i = 0; i < LAST_PACKET_REDUNDANCY; i++) {
    notifyAudioPacket(payload, payloadLen, true);
    delay(3);
  }
}

void sendMicTransferStep() {
  if (!micTransferPending) return;

  int packetsSentThisLoop = 0;

  while (packetsSentThisLoop < AUDIO_PACKETS_PER_LOOP && micTransferPending) {
    if (micTransferZeroOnly) {
      sendLastPacketWithRedundancy(nullptr, 0);
      micTransferPending = false;
      micTransferZeroOnly = false;
      break;
    }

    if (micTransferOffset >= micBufferedBytes) {
      micTransferPending = false;
      break;
    }

    const size_t remaining = micBufferedBytes - micTransferOffset;
    const uint16_t payloadLen = static_cast<uint16_t>(
        remaining > AUDIO_PACKET_PAYLOAD_BYTES ? AUDIO_PACKET_PAYLOAD_BYTES : remaining);

    const bool isLast = (micTransferOffset + payloadLen) >= micBufferedBytes;
    const uint8_t *payloadPtr = micPcmBuffer + micTransferOffset;

    if (isLast) {
      sendLastPacketWithRedundancy(payloadPtr, payloadLen);
      micTransferOffset += payloadLen;
      packetsSentThisLoop++;
      micTransferPending = false;
      break;
    }

    notifyAudioPacket(payloadPtr, payloadLen, false);
    micTransferOffset += payloadLen;
    packetsSentThisLoop++;
  }

  if (!micTransferPending) {
    const unsigned long elapsed = millis() - micTransferStartMs;
    Serial.printf("[MIC] TRANSFER_DONE session=%lu bytes=%u packets=%lu elapsedMs=%lu connected=%s\n",
                  static_cast<unsigned long>(micSessionId),
                  static_cast<unsigned>(micBufferedBytes),
                  static_cast<unsigned long>(micTransferPacketsSent),
                  elapsed,
                  centralConnected ? "YES" : "NO");
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

  audioChar = service->createCharacteristic(
      AUDIO_CHARACTERISTIC_UUID,
      BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  audioChar->addDescriptor(new BLE2902());
  uint8_t audioInit = 0x00;
  audioChar->setValue(&audioInit, 1);

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
  Serial.printf("[CFG] MIC_BUTTON_PIN=%d\n", MIC_BUTTON_PIN);
  Serial.printf("[CFG] INMP441 WS=%d SCK=%d SD=%d\n", I2S_WS_PIN, I2S_SCK_PIN, I2S_SD_PIN);
  Serial.printf("[CFG] Audio total=%u payload=%u packetsPerLoop=%u loopDelayMs=%lu\n",
                static_cast<unsigned>(AUDIO_PACKET_TOTAL_BYTES),
                static_cast<unsigned>(AUDIO_PACKET_PAYLOAD_BYTES),
                static_cast<unsigned>(AUDIO_PACKETS_PER_LOOP),
                LOOP_DELAY_MS);
  Serial.printf("[MEM] boot psramFound=%s freePsram=%u freeHeap=%u\n",
                psramFound() ? "YES" : "NO",
                static_cast<unsigned>(ESP.getFreePsram()),
                static_cast<unsigned>(ESP.getFreeHeap()));

  pinMode(BUTTON_PIN, INPUT_PULLUP);
  lastRawButton = digitalRead(BUTTON_PIN);
  stableButton = lastRawButton;

  pinMode(MIC_BUTTON_PIN, INPUT_PULLUP);
  lastRawMicButton = digitalRead(MIC_BUTTON_PIN);
  stableMicButton = lastRawMicButton;

  setupBlePeripheral();

  if (!setupI2sInput()) {
    Serial.println("[MIC] WARNING: I2S init failed; mic feature unavailable");
  }

  Serial.println("Ready. Desk: single press=UP/STOP, double press=DOWN/STOP. Mic: Pin12 tap=start/stop.");
}

void loop() {
  // Keep desk control unchanged
  processButton();

  // Mic flow
  processMicButton();
  captureMicIfRecording();
  sendMicTransferStep();

  if (DEBUG_BUTTON || DEBUG_MIC) {
    const unsigned long now = millis();
    if ((now - lastPinDebugMs) >= PIN_DEBUG_INTERVAL_MS) {
      lastPinDebugMs = now;
      Serial.printf(
          "[PIN] deskRaw=%u deskStable=%u click=%u micRaw=%u micStable=%u micRec=%s micTx=%s connected=%s\n",
          digitalRead(BUTTON_PIN),
          stableButton,
          clickCount,
          digitalRead(MIC_BUTTON_PIN),
          stableMicButton,
          micRecording ? "YES" : "NO",
          micTransferPending ? "YES" : "NO",
          centralConnected ? "YES" : "NO");
    }
  }

  delay(LOOP_DELAY_MS);
}