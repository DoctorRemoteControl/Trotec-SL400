package de.drremote.trotecsl400.audio

import android.content.Context
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

class AlarmAudioCaptureCoordinator(
    private val context: Context,
    private val selector: AudioInputDeviceSelector = AudioInputDeviceSelector(context)
) {
    data class Status(
        val message: String,
        val isRecording: Boolean = false,
        val deviceInfo: String = "None",
        val sampleRate: Int = 0,
        val bufferSeconds: Int = 0,
        val lastClipPath: String = ""
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val isRunning = AtomicBoolean(false)
    private val isCapturing = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var ringBuffer: AudioRingBuffer? = null
    private var sampleRate: Int = 0
    private var channels: Int = 1
    private var lastDeviceInfo: String = "None"
    private var lastClipPath: String = ""

    fun start(
        bufferSeconds: Int = DEFAULT_BUFFER_SECONDS,
        onStatus: (Status) -> Unit
    ) {
        if (isRunning.getAndSet(true)) {
            emit(onStatus, status("Already running"))
            return
        }

        thread(name = "alarm-audio-ringbuffer") {
            val device = selector.findPreferredInput()
            lastDeviceInfo = selector.describe(device)
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            channels = 1
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            sampleRate = pickSampleRate(device, channelConfig, audioFormat)
            val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = minBuffer.coerceAtLeast(sampleRate / 2)

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
                isRunning.set(false)
                emit(onStatus, status("AudioRecord init failed"))
                record.release()
                return@thread
            }

            val bytesPerSecond = sampleRate * channels * BYTES_PER_SAMPLE
            ringBuffer = AudioRingBuffer(bytesPerSecond * bufferSeconds)
            audioRecord = record

            emit(onStatus, status("Audio capture running", bufferSeconds))

            val buffer = ByteArray(bufferSize)
            record.startRecording()
            try {
                while (isRunning.get()) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        ringBuffer?.write(buffer, read)
                    }
                }
            } catch (t: Throwable) {
                emit(onStatus, status("Audio capture error: ${t.message}", bufferSeconds))
            } finally {
                runCatching { record.stop() }
                runCatching { record.release() }
                audioRecord = null
                ringBuffer?.clear()
                isRunning.set(false)
                emit(onStatus, status("Audio capture stopped", bufferSeconds))
            }
        }
    }

    fun stop(onStatus: (Status) -> Unit) {
        if (!isRunning.get()) {
            emit(onStatus, status("Not running"))
            return
        }
        isRunning.set(false)
        emit(onStatus, status("Stopping..."))
    }

    fun captureIncidentClip(
        alertId: String,
        preRollMs: Long = DEFAULT_PRE_ROLL_MS,
        postRollMs: Long = DEFAULT_POST_ROLL_MS,
        onStatus: (Status) -> Unit
    ) {
        if (!isRunning.get()) {
            emit(onStatus, status("Audio capture not running"))
            return
        }
        if (isCapturing.getAndSet(true)) {
            emit(onStatus, status("Incident capture already in progress"))
            return
        }
        thread(name = "alarm-audio-capture") {
            try {
                emit(onStatus, status("Capturing incident audio..."))
                Thread.sleep(postRollMs)

                val buffer = ringBuffer
                if (buffer == null || sampleRate <= 0) {
                    emit(onStatus, status("Audio buffer not ready"))
                    return@thread
                }

                val totalMs = preRollMs + postRollMs
                val bytesPerMs = sampleRate * channels * BYTES_PER_SAMPLE / 1000
                val totalBytes = (bytesPerMs * totalMs).toInt()
                val pcm = buffer.snapshotLast(totalBytes)

                val outDir = File(context.filesDir, "audio_incidents")
                outDir.mkdirs()
                val fileName = "incident_${alertId}_${timestamp()}.wav"
                val outFile = File(outDir, fileName)
                WavWriter(outFile, sampleRate, channels, 16).use { it.writePcm(pcm, pcm.size) }
                lastClipPath = outFile.absolutePath

                emit(onStatus, status("Incident clip saved", lastClipPath = lastClipPath))
            } finally {
                isCapturing.set(false)
            }
        }
    }

    fun isRunning(): Boolean = isRunning.get()

    private fun status(
        message: String,
        bufferSeconds: Int = DEFAULT_BUFFER_SECONDS,
        lastClipPath: String = this.lastClipPath
    ): Status {
        return Status(
            message = message,
            isRecording = isRunning.get(),
            deviceInfo = lastDeviceInfo,
            sampleRate = sampleRate,
            bufferSeconds = bufferSeconds,
            lastClipPath = lastClipPath
        )
    }

    private fun pickSampleRate(
        device: android.media.AudioDeviceInfo?,
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

    private companion object {
        const val BYTES_PER_SAMPLE = 2
        const val DEFAULT_BUFFER_SECONDS = 30
        const val DEFAULT_PRE_ROLL_MS = 10_000L
        const val DEFAULT_POST_ROLL_MS = 20_000L
    }
}
