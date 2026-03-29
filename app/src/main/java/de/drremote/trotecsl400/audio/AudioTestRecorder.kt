package de.drremote.trotecsl400.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class AudioTestRecorder(
    private val context: Context,
    private val selector: AudioInputDeviceSelector = AudioInputDeviceSelector(context)
) {
    data class Status(
        val message: String,
        val deviceInfo: String = "None",
        val sampleRate: Int = 0,
        val channels: Int = 0,
        val filePath: String = ""
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val isRecording = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var activeFile: File? = null

    fun startTestRecording(
        durationMs: Long,
        onStatus: (Status) -> Unit
    ) {
        if (isRecording.getAndSet(true)) {
            emit(onStatus, Status("Already recording"))
            return
        }

        thread(name = "usb-audio-test") {
            val device = selector.findPreferredInput()
            val deviceInfo = selector.describe(device)
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val channels = 1
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val sampleRate = pickSampleRate(device, channelConfig, audioFormat)
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                .coerceAtLeast(sampleRate / 2)

            val record = AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (device != null) {
                record.preferredDevice = device
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                isRecording.set(false)
                emit(onStatus, Status("AudioRecord init failed", deviceInfo))
                record.release()
                return@thread
            }

            val outDir = File(context.filesDir, "audio_test")
            outDir.mkdirs()
            val fileName = "usb_test_${timestamp()}.wav"
            val outFile = File(outDir, fileName)
            activeFile = outFile

            emit(
                onStatus,
                Status(
                    message = "Recording...",
                    deviceInfo = deviceInfo,
                    sampleRate = sampleRate,
                    channels = channels,
                    filePath = outFile.absolutePath
                )
            )

            val writer = WavWriter(outFile, sampleRate, channels, 16)
            val buffer = ByteArray(bufferSize)
            val startMs = System.currentTimeMillis()
            record.startRecording()
            audioRecord = record

            try {
                while (isRecording.get()) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        writer.writePcm(buffer, read)
                    }
                    val elapsed = System.currentTimeMillis() - startMs
                    if (elapsed >= durationMs) {
                        break
                    }
                }
            } catch (t: Throwable) {
                emit(onStatus, Status("Recording error: ${t.message}", deviceInfo))
            } finally {
                runCatching { record.stop() }
                runCatching { record.release() }
                audioRecord = null
                writer.close()
                isRecording.set(false)
            }

            emit(
                onStatus,
                Status(
                    message = "Recording saved",
                    deviceInfo = deviceInfo,
                    sampleRate = sampleRate,
                    channels = channels,
                    filePath = outFile.absolutePath
                )
            )
        }
    }

    fun stop(onStatus: (Status) -> Unit) {
        if (!isRecording.get()) {
            emit(onStatus, Status("Not recording"))
            return
        }
        isRecording.set(false)
        val filePath = activeFile?.absolutePath ?: ""
        emit(onStatus, Status("Stopping...", filePath = filePath))
    }

    fun isRecording(): Boolean = isRecording.get()

    private fun pickSampleRate(
        device: AudioDeviceInfo?,
        channelConfig: Int,
        audioFormat: Int
    ): Int {
        val candidates = listOf(48_000, 44_100, 32_000, 16_000)
        val rates = device?.sampleRates?.toList().orEmpty()
        val preferred = rates.firstOrNull { it in candidates }
        val search = if (preferred != null) listOf(preferred) + candidates else candidates
        return search.firstOrNull {
            AudioRecord.getMinBufferSize(it, channelConfig, audioFormat) > 0
        } ?: candidates.last()
    }

    private fun emit(onStatus: (Status) -> Unit, status: Status) {
        mainHandler.post { onStatus(status) }
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }
}
