package com.desk.moodboard.voice

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.concurrent.TimeUnit

@Serializable
data class LlmAsrUser(
    val uid: String
)

@Serializable
data class LlmAsrAudio(
    val format: String = "wav",
    val codec: String = "raw",
    val rate: Int = 16000,
    val bits: Int = 16,
    val channel: Int = 1
)

@Serializable
data class LlmAsrRequest(
    val model_name: String = "bigmodel",
    val enable_itn: Boolean = true,
    val enable_punc: Boolean = true,
    val enable_ddc: Boolean = true,
    val show_utterances: Boolean = true,
    val enable_nonstream: Boolean = false
)

@Serializable
data class LlmAsrPayload(
    val user: LlmAsrUser,
    val audio: LlmAsrAudio,
    val request: LlmAsrRequest
)

private data class AudioInfo(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int
)

private data class ParsedResponse(
    val code: Int,
    val isLast: Boolean,
    val payloadMsg: String
)

class VolcengineASRService(
    private val appid: String,
    private val token: String,
    private val resourceId: String
) {
    companion object {
        private const val TAG = "VolcengineASR"
        private const val WS_URL = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_nostream"

        private const val PROTOCOL_VERSION = 0b0001
        private const val DEFAULT_HEADER_SIZE = 0b0001
        private const val CLIENT_FULL_REQUEST = 0b0001
        private const val CLIENT_AUDIO_ONLY_REQUEST = 0b0010
        private const val SERVER_FULL_RESPONSE = 0b1001
        private const val SERVER_ERROR_RESPONSE = 0b1111

        private const val NO_SEQUENCE = 0b0000
        private const val POS_SEQUENCE = 0b0001
        private const val NEG_SEQUENCE = 0b0010
        private const val NEG_WITH_SEQUENCE = 0b0011

        private const val JSON_SERIALIZATION = 0b0001
        private const val GZIP_COMPRESSION = 0b0001
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()
    private var webSocket: WebSocket? = null
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    private val _transcriptFlow = MutableSharedFlow<String>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val transcriptFlow: SharedFlow<String> = _transcriptFlow
    private var sequence: Int = 1
    private val audioBuffer = ByteArrayOutputStream()
    private val segmentBytes = (16000 * 2 * 200) / 1000 // 200ms, 16kHz, 16-bit mono
    @Volatile private var fullRequestSent = false
    private var finalResultDeferred: CompletableDeferred<String?>? = null
    private var hasLoggedBuffering = false
    private val sendScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val segmentChannel = Channel<Segment>(Channel.UNLIMITED)
    private var senderJob: Job? = null

    fun startStreaming() {
        Log.d(TAG, "ASR: startStreaming() called. AppID: $appid")
        sequence = 1
        audioBuffer.reset()
        fullRequestSent = false
        finalResultDeferred = CompletableDeferred()
        hasLoggedBuffering = false
        senderJob?.cancel()
        senderJob = sendScope.launch {
            while (!fullRequestSent) {
                delay(10)
            }
            for (segment in segmentChannel) {
                val ws = webSocket ?: continue
                sendAudioSegment(ws, segment.bytes, segment.isLast)
                delay(200)
                if (segment.isLast) {
                    break
                }
            }
        }

        val request = Request.Builder()
            .url(WS_URL)
            .header("X-Api-App-Key", appid)
            .header("X-Api-Access-Key", token)
            .header("X-Api-Resource-Id", resourceId)
            .header("X-Api-Connect-Id", UUID.randomUUID().toString())
            .build()

        Log.d(TAG, "ASR: Connecting to $WS_URL with LLM ASR headers")
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "ASR: WebSocket Opened Successfully. Sending Full Request.")
                sendFullClientRequest(webSocket)
                fullRequestSent = true
                Log.d(TAG, "ASR: Full request sent, flushing buffered audio if any.")
                flushBufferedAudio(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleResponse(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "ASR: WebSocket Failure!", t)
                Log.e(TAG, "ASR: Response Code: ${response?.code}, Message: ${response?.message}")
                response?.body?.let { 
                    try { Log.e(TAG, "ASR: Error Body: ${it.string()}") } catch(e: Exception) {}
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "ASR: WebSocket Closing: $code / $reason")
            }
        })
    }

    suspend fun transcribePcm(pcm: ShortArray): String? {
        val wavBytes = buildWavBytes(pcm)
        val audioInfo = parseWavInfo(wavBytes)
        val segmentSize = calculateSegmentSize(audioInfo)
        val segments = splitAudio(wavBytes, segmentSize)

        val resultDeferred = CompletableDeferred<String?>()
        var lastText: String? = null

        val request = Request.Builder()
            .url(WS_URL)
            .header("X-Api-App-Key", appid)
            .header("X-Api-Access-Key", token)
            .header("X-Api-Resource-Id", resourceId)
            .header("X-Api-Connect-Id", UUID.randomUUID().toString())
            .build()

        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "ASR: LLM socket opened, sending full request + audio segments.")
                sendFullClientRequest(webSocket)
                sendScope.launch {
                    for (i in segments.indices) {
                        val segment = segments[i]
                        val isLast = i == segments.lastIndex
                        sendAudioSegment(webSocket, segment, isLast)
                        delay(200)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val rawBytes = text.toByteArray(Charsets.ISO_8859_1)
                Log.d(TAG, "ASR: Received TEXT frame as bytes len=${rawBytes.size}")

                // Some servers send gzip-compressed payloads in TEXT frames
                if (rawBytes.size >= 2 && rawBytes[0] == 0x1f.toByte() && rawBytes[1] == 0x8b.toByte()) {
                    val jsonStr = try { String(decompress(rawBytes)) } catch (e: Exception) { null }
                    if (!jsonStr.isNullOrBlank()) {
                        Log.d(TAG, "ASR: TEXT gzip->JSON: $jsonStr")
                        val textResult = extractText(jsonStr)
                        if (!textResult.isNullOrBlank()) {
                            lastText = textResult
                            resultDeferred.complete(textResult)
                            return
                        }
                    }
                }

                val parsed = parseResponse(rawBytes)
                if (parsed != null) {
                    Log.d(TAG, "ASR: Parsed response code=${parsed.code}, isLast=${parsed.isLast}")
                    Log.d(TAG, "ASR: PayloadMsg=${parsed.payloadMsg}")
                    val textResult = extractText(parsed.payloadMsg)
                    if (!textResult.isNullOrBlank()) {
                        lastText = textResult
                    }
                    if (parsed.isLast || parsed.code != 0) {
                        // FIX: Only complete with extracted text, never raw payloadMsg
                        resultDeferred.complete(lastText ?: "")
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "ASR: Received BINARY response size=${bytes.size}")
                val parsed = parseResponse(bytes.toByteArray())
                if (parsed != null) {
                    Log.d(TAG, "ASR: Parsed response code=${parsed.code}, isLast=${parsed.isLast}")
                    Log.d(TAG, "ASR: PayloadMsg=${parsed.payloadMsg}")
                    val text = extractText(parsed.payloadMsg)
                    if (!text.isNullOrBlank()) {
                        lastText = text
                    }
                    if (parsed.isLast) {
                        // FIX: Only complete with extracted text, never raw payloadMsg
                        resultDeferred.complete(lastText ?: "")
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "ASR: LLM socket failure", t)
                resultDeferred.complete(null)
            }
        })

        val result = resultDeferred.await()
        ws.close(1000, "done")
        return result
    }

    private fun sendFullClientRequest(webSocket: WebSocket) {
        val payload = LlmAsrPayload(
            user = LlmAsrUser(uid = "android_user"),
            audio = LlmAsrAudio(),
            request = LlmAsrRequest()
        )
        val payloadStr = json.encodeToString(LlmAsrPayload.serializer(), payload)
        Log.d(TAG, "ASR: Full Request JSON: $payloadStr")

        val payloadBytes = compress(payloadStr.toByteArray(Charsets.UTF_8))
        val header = getHeader(CLIENT_FULL_REQUEST, NO_SEQUENCE, JSON_SERIALIZATION, GZIP_COMPRESSION, 0)
        val payloadSize = intToBytes(payloadBytes.size)

        val fullRequest = ByteArray(header.size + payloadSize.size + payloadBytes.size)
        System.arraycopy(header, 0, fullRequest, 0, header.size)
        System.arraycopy(payloadSize, 0, fullRequest, header.size, payloadSize.size)
        System.arraycopy(payloadBytes, 0, fullRequest, header.size + payloadSize.size, payloadBytes.size)
        webSocket.send(fullRequest.toByteString())
    }

    private fun flushBufferedAudio(webSocket: WebSocket) {
        while (audioBuffer.size() >= segmentBytes) {
            val segment = audioBuffer.toByteArray().copyOfRange(0, segmentBytes)
            val remaining = audioBuffer.toByteArray().copyOfRange(segmentBytes, audioBuffer.size())
            audioBuffer.reset()
            audioBuffer.write(remaining)

            segmentChannel.trySend(Segment(segment, false))
        }
    }

    fun sendAudioChunk(chunk: ShortArray, isLast: Boolean = false) {
        val ws = webSocket ?: return
        Log.d(TAG, "ASR: sendAudioChunk() called. Size: ${chunk.size}, isLast: $isLast")
        if (chunk.isNotEmpty()) {
            val byteBuffer = ByteBuffer.allocate(chunk.size * 2).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                for (s in chunk) putShort(s)
            }
            audioBuffer.write(byteBuffer.array())
        }

        if (!fullRequestSent) {
            if (!hasLoggedBuffering) {
                Log.d(TAG, "ASR: Buffering audio until full request is sent.")
                hasLoggedBuffering = true
            }
            return
        }

        while (audioBuffer.size() >= segmentBytes) {
            val segment = audioBuffer.toByteArray().copyOfRange(0, segmentBytes)
            val remaining = audioBuffer.toByteArray().copyOfRange(segmentBytes, audioBuffer.size())
            audioBuffer.reset()
            audioBuffer.write(remaining)

            segmentChannel.trySend(Segment(segment, false))
        }

        if (isLast) {
            val remaining = audioBuffer.toByteArray()
            audioBuffer.reset()
            if (remaining.isNotEmpty()) {
                segmentChannel.trySend(Segment(remaining, true))
            } else {
                segmentChannel.trySend(Segment(ByteArray(0), true))
            }
        }
    }

    fun stopStreaming() {
        Log.d(TAG, "ASR: stopStreaming() called")
        senderJob?.cancel()
        senderJob = null
        webSocket?.close(1000, "User stopped")
        webSocket = null
    }

    suspend fun awaitFinalResult(): String? {
        return finalResultDeferred?.await()
    }

    private fun handleResponse(data: ByteArray) {
        if (data.size < 4) return
        
        val messageType = (data[1].toInt() shr 4) and 0x0f
        val messageTypeSpecificFlags = data[1].toInt() and 0x0f
        Log.d(TAG, "ASR: Response header type=$messageType flags=$messageTypeSpecificFlags size=${data.size}")
        val serialization = (data[2].toInt() shr 4) and 0x0f
        val compression = data[2].toInt() and 0x0f
        val headerSize = data[0].toInt() and 0x0f
        
        val payloadOffset = headerSize * 4
        if (data.size <= payloadOffset) return
        
        val payload = data.copyOfRange(payloadOffset, data.size)

        var mutablePayload = payload
        var payloadSequence = 0
        var isLastPackage = false
        if ((messageTypeSpecificFlags and 0x01) != 0) {
            payloadSequence = bytesToInt(mutablePayload.copyOfRange(0, 4))
            mutablePayload = mutablePayload.copyOfRange(4, mutablePayload.size)
        }
        if ((messageTypeSpecificFlags and 0x02) != 0) {
            isLastPackage = true
        }
        if ((messageTypeSpecificFlags and 0x04) != 0) {
            mutablePayload = mutablePayload.copyOfRange(4, mutablePayload.size)
        }
        if (payloadSequence != 0 || isLastPackage) {
            Log.d(TAG, "ASR: Response seq=$payloadSequence isLast=$isLastPackage")
        }

        val payloadMsg: ByteArray = when (messageType) {
            SERVER_FULL_RESPONSE -> {
                if (mutablePayload.size < 4) return
                mutablePayload.copyOfRange(4, mutablePayload.size)
            }
            SERVER_ERROR_RESPONSE -> {
                if (mutablePayload.size < 8) return
                mutablePayload.copyOfRange(8, mutablePayload.size)
            }
            else -> return
        }

        if (payloadMsg.isEmpty()) return

        val decompressed = if (compression == GZIP_COMPRESSION) {
            try { decompress(payloadMsg) } catch(e: Exception) { return }
        } else {
            payloadMsg
        }
        
        if (serialization == JSON_SERIALIZATION) {
            val jsonStr = String(decompressed)
            Log.d(TAG, "ASR: Received JSON: $jsonStr")
            val text = extractText(jsonStr)
            if (!text.isNullOrBlank()) {
                Log.d(TAG, "ASR: Transcript: $text")
                _transcriptFlow.tryEmit(text)
                if (isLastPackage) {
                    finalResultDeferred?.complete(text)
                }
            } else if (isLastPackage && jsonStr.isNotBlank()) {
                // FIX: Only emit extracted text or empty string, never the raw JSON
                finalResultDeferred?.complete("")
            }
        }
    }

    private fun parseResponse(res: ByteArray): ParsedResponse? {
        if (res.isEmpty()) return null
        val headerSize = res[0].toInt() and 0x0f
        val messageType = (res[1].toInt() shr 4) and 0x0f
        val messageTypeSpecificFlags = res[1].toInt() and 0x0f
        val serializationMethod = (res[2].toInt() shr 4) and 0x0f
        val messageCompression = res[2].toInt() and 0x0f

        var payload = res.copyOfRange(headerSize * 4, res.size)
        var isLastPackage = false

        if ((messageTypeSpecificFlags and 0x01) != 0) {
            payload = payload.copyOfRange(4, payload.size)
        }
        if ((messageTypeSpecificFlags and 0x02) != 0) {
            isLastPackage = true
        }
        if ((messageTypeSpecificFlags and 0x04) != 0) {
            payload = payload.copyOfRange(4, payload.size)
        }

        var code = 0
        when (messageType) {
            SERVER_FULL_RESPONSE -> {
                if (payload.size < 4) return null
                payload = payload.copyOfRange(4, payload.size)
            }
            SERVER_ERROR_RESPONSE -> {
                if (payload.size < 8) return null
                code = bytesToInt(payload.copyOfRange(0, 4))
                payload = payload.copyOfRange(8, payload.size)
            }
            else -> return null
        }

        if (messageCompression == GZIP_COMPRESSION && payload.isNotEmpty()) {
            payload = try { decompress(payload) } catch (e: Exception) { return null }
        }

        val payloadMsg = if (serializationMethod == JSON_SERIALIZATION && payload.isNotEmpty()) {
            String(payload)
        } else {
            ""
        }

        Log.d(TAG, "ASR: parseResponse code=$code isLast=$isLastPackage payloadLen=${payload.size}")
        return ParsedResponse(code = code, isLast = isLastPackage, payloadMsg = payloadMsg)
    }

    private fun sendAudioSegment(webSocket: WebSocket, audio: ByteArray, isLast: Boolean) {
        Log.d(TAG, "ASR: Sending audio segment size=${audio.size} isLast=$isLast")
        val flags = if (isLast) NEG_SEQUENCE else NO_SEQUENCE
        val header = getHeader(CLIENT_AUDIO_ONLY_REQUEST, flags, JSON_SERIALIZATION, GZIP_COMPRESSION, 0)
        val payloadBytes = compress(audio)
        val payloadSize = intToBytes(payloadBytes.size)

        val audioRequest = ByteArray(header.size + payloadSize.size + payloadBytes.size)
        System.arraycopy(header, 0, audioRequest, 0, header.size)
        System.arraycopy(payloadSize, 0, audioRequest, header.size, payloadSize.size)
        System.arraycopy(payloadBytes, 0, audioRequest, header.size + payloadSize.size, payloadBytes.size)
        webSocket.send(audioRequest.toByteString())
    }

    private fun getHeader(messageType: Int, messageTypeSpecificFlags: Int, serialMethod: Int, compressionType: Int, reservedData: Int): ByteArray {
        val header = ByteArray(4)
        header[0] = ((PROTOCOL_VERSION shl 4) or DEFAULT_HEADER_SIZE).toByte()
        header[1] = ((messageType shl 4) or messageTypeSpecificFlags).toByte()
        header[2] = ((serialMethod shl 4) or compressionType).toByte()
        header[3] = reservedData.toByte()
        return header
    }

    private fun buildWavBytes(pcm: ShortArray): ByteArray {
        val pcmBytes = ByteBuffer.allocate(pcm.size * 2).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcm) putShort(s)
        }.array()

        val header = ByteArray(44)
        val totalDataLen = pcmBytes.size
        val totalLen = totalDataLen + 36

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        writeIntLE(header, 4, totalLen)
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        writeIntLE(header, 16, 16)
        writeShortLE(header, 20, 1)
        writeShortLE(header, 22, 1)
        writeIntLE(header, 24, 16000)
        writeIntLE(header, 28, 16000 * 2)
        writeShortLE(header, 32, 2)
        writeShortLE(header, 34, 16)

        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        writeIntLE(header, 40, totalDataLen)

        return header + pcmBytes
    }

    private fun parseWavInfo(data: ByteArray): AudioInfo {
        if (data.size < 44) {
            return AudioInfo(16000, 1, 16)
        }
        val channels = ((data[23].toInt() and 0xFF) shl 8) or (data[22].toInt() and 0xFF)
        val sampleRate = ((data[27].toInt() and 0xFF) shl 24) or
            ((data[26].toInt() and 0xFF) shl 16) or
            ((data[25].toInt() and 0xFF) shl 8) or
            (data[24].toInt() and 0xFF)
        val bitsPerSample = ((data[35].toInt() and 0xFF) shl 8) or (data[34].toInt() and 0xFF)
        return AudioInfo(sampleRate, channels, bitsPerSample)
    }

    private fun calculateSegmentSize(audioInfo: AudioInfo): Int {
        val sampWidth = audioInfo.bitsPerSample / 8
        val bytesPerSec = audioInfo.channels * sampWidth * audioInfo.sampleRate
        return bytesPerSec * 200 / 1000
    }

    private fun splitAudio(audioData: ByteArray, segmentSize: Int): List<ByteArray> {
        val segments = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < audioData.size) {
            val len = minOf(segmentSize, audioData.size - offset)
            segments.add(audioData.copyOfRange(offset, offset + len))
            offset += len
        }
        return segments
    }

    private fun writeIntLE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShortLE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun extractText(jsonStr: String): String? {
        return try {
            val jsonElement = json.parseToJsonElement(jsonStr)
            val jsonObject = jsonElement as? JsonObject ?: return null
            val directText = jsonObject["text"]?.let { (it as? JsonPrimitive)?.content }
            if (!directText.isNullOrBlank()) return directText
            val result = jsonObject["result"]
            when (result) {
                is JsonArray -> {
                    result.firstOrNull()?.let { it as? JsonObject }?.get("text")?.let { (it as? JsonPrimitive)?.content }
                }
                is JsonObject -> {
                    result["text"]?.let { (it as? JsonPrimitive)?.content }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            ((value shr 24) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }

    private fun bytesToInt(src: ByteArray): Int {
        if (src.size != 4) return 0
        return ((src[0].toInt() and 0xFF) shl 24) or
            ((src[1].toInt() and 0xFF) shl 16) or
            ((src[2].toInt() and 0xFF) shl 8) or
            (src[3].toInt() and 0xFF)
    }

    private data class Segment(val bytes: ByteArray, val isLast: Boolean)

    private fun compress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(data.size)
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun decompress(data: ByteArray): ByteArray {
        return GZIPInputStream(data.inputStream()).readBytes()
    }
}
