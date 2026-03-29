package de.drremote.trotecsl400.audio

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.sqrt

data class AudioHintResult(
    val label: String,
    val confidence: Double,
    val sourceHint: String,
    val clippedRatio: Double,
    val dcOffset: Double,
    val subRatio: Double,
    val bassRatio: Double,
    val midRatio: Double,
    val highRatio: Double,
    val rms: Double,
    val crest: Double,
    val zcr: Double,
    val lowPulseRate: Double,
    val lowPulseRegularity: Double
)

object AudioHintAnalyzer {
    fun analyzeWav(file: File): AudioHintResult? {
        val data = runCatching { FileInputStream(file).use { it.readBytes() } }.getOrNull()
            ?: return null
        val wav = parseWav(data) ?: return null
        return analyzeFrames(wav)
    }

    fun analyzePcm16(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int
    ): AudioHintResult? {
        if (pcmData.isEmpty() || sampleRate <= 0 || channels <= 0) return null
        val mono = if (channels == 1) {
            pcmData
        } else {
            downmixToMonoPcm16(pcmData, channels)
        }
        val wav = WavData(
            sampleRate = sampleRate,
            channels = 1,
            bitsPerSample = 16,
            pcmData = mono
        )
        return analyzeFrames(wav)
    }

    private data class WavData(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val pcmData: ByteArray
    )

    private data class FrameFeatures(
        val subRatio: Double,
        val bassRatio: Double,
        val midRatio: Double,
        val highRatio: Double,
        val rms: Double,
        val crest: Double,
        val zcr: Double,
        val lowEnergy: Double
    )

    private fun parseWav(bytes: ByteArray): WavData? {
        if (bytes.size < 44) return null
        if (!bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray())) return null
        if (!bytes.copyOfRange(8, 12).contentEquals("WAVE".toByteArray())) return null

        var offset = 12
        var sampleRate = 0
        var channels = 0
        var bitsPerSample = 0
        var dataOffset = -1
        var dataSize = 0

        while (offset + 8 <= bytes.size) {
            val chunkId = bytes.copyOfRange(offset, offset + 4)
            val size = toIntLE(bytes, offset + 4)
            val chunkDataStart = offset + 8
            val chunkDataEnd = chunkDataStart + size
            if (chunkDataEnd > bytes.size) break

            when (String(chunkId)) {
                "fmt " -> {
                    val audioFormat = toShortLE(bytes, chunkDataStart).toInt()
                    channels = toShortLE(bytes, chunkDataStart + 2).toInt()
                    sampleRate = toIntLE(bytes, chunkDataStart + 4)
                    bitsPerSample = toShortLE(bytes, chunkDataStart + 14).toInt()
                    if (audioFormat != 1) return null // PCM only
                }
                "data" -> {
                    dataOffset = chunkDataStart
                    dataSize = size
                }
            }
            offset = chunkDataEnd + (size and 1)
        }

        if (dataOffset < 0 || dataSize <= 0) return null
        if (sampleRate <= 0 || channels <= 0 || bitsPerSample != 16) return null
        val end = (dataOffset + dataSize).coerceAtMost(bytes.size)
        return WavData(sampleRate, channels, bitsPerSample, bytes.copyOfRange(dataOffset, end))
    }

    private fun analyzeFrames(wav: WavData): AudioHintResult? {
        val bytes = wav.pcmData
        if (bytes.isEmpty()) return null
        val samples = bytes.size / 2
        if (samples <= 0) return null

        val dt = 1.0 / wav.sampleRate.toDouble()
        val alphaSub = alpha(80.0, dt)
        val alphaBass = alpha(200.0, dt)
        val alphaMid = alpha(2000.0, dt)
        val alphaHigh = alphaHigh(2000.0, dt)

        var lpSub = 0.0
        var lpBass = 0.0
        var lpMid = 0.0
        var hpHigh = 0.0
        var prevX = 0.0

        val frameSize = (wav.sampleRate * FRAME_MS / 1000.0).toInt().coerceAtLeast(256)
        var subEnergy = 0.0
        var bassEnergy = 0.0
        var midEnergy = 0.0
        var highEnergy = 0.0
        var sumSq = 0.0
        var maxAbs = 0.0
        var count = 0
        var zeroCrossings = 0
        var prevSign = 0

        var clippedSamples = 0
        var totalSamples = 0
        var sumSamples = 0.0
        val frames = ArrayList<FrameFeatures>()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        while (buffer.remaining() >= 2) {
            val raw = buffer.short.toInt()
            val x = raw / 32768.0
            val absX = abs(x)
            if (absX > maxAbs) maxAbs = absX
            if (absX >= CLIP_THRESHOLD) clippedSamples++
            totalSamples++
            sumSamples += x
            val sign = if (x >= 0.0) 1 else -1
            if (prevSign != 0 && sign != prevSign) zeroCrossings++
            prevSign = sign

            lpSub += alphaSub * (x - lpSub)
            lpBass += alphaBass * (x - lpBass)
            lpMid += alphaMid * (x - lpMid)
            hpHigh = alphaHigh * (hpHigh + x - prevX)
            prevX = x

            val sub = lpSub
            val bass = lpBass - lpSub
            val mid = lpMid - lpBass
            val high = hpHigh

            subEnergy += sub * sub
            bassEnergy += bass * bass
            midEnergy += mid * mid
            highEnergy += high * high
            sumSq += x * x
            count++

            if (count >= frameSize) {
                val rms = sqrt(sumSq / count)
                val crest = if (rms > 0.0) maxAbs / rms else 0.0
                val totalEnergy = subEnergy + bassEnergy + midEnergy + highEnergy
                val subRatio = if (totalEnergy > 0.0) subEnergy / totalEnergy else 0.0
                val bassRatio = if (totalEnergy > 0.0) bassEnergy / totalEnergy else 0.0
                val midRatio = if (totalEnergy > 0.0) midEnergy / totalEnergy else 0.0
                val highRatio = if (totalEnergy > 0.0) highEnergy / totalEnergy else 0.0
                val zcr = zeroCrossings.toDouble() / count.toDouble()
                frames.add(
                    FrameFeatures(
                        subRatio = subRatio,
                        bassRatio = bassRatio,
                        midRatio = midRatio,
                        highRatio = highRatio,
                        rms = rms,
                        crest = crest,
                        zcr = zcr,
                        lowEnergy = (subEnergy + bassEnergy) / count.toDouble()
                    )
                )
                subEnergy = 0.0
                bassEnergy = 0.0
                midEnergy = 0.0
                highEnergy = 0.0
                sumSq = 0.0
                maxAbs = 0.0
                count = 0
                zeroCrossings = 0
            }
        }

        if (count >= frameSize / 2) {
            val rms = sqrt(sumSq / count)
            val crest = if (rms > 0.0) maxAbs / rms else 0.0
            val totalEnergy = subEnergy + bassEnergy + midEnergy + highEnergy
            val subRatio = if (totalEnergy > 0.0) subEnergy / totalEnergy else 0.0
            val bassRatio = if (totalEnergy > 0.0) bassEnergy / totalEnergy else 0.0
            val midRatio = if (totalEnergy > 0.0) midEnergy / totalEnergy else 0.0
            val highRatio = if (totalEnergy > 0.0) highEnergy / totalEnergy else 0.0
            val zcr = zeroCrossings.toDouble() / count.toDouble()
            frames.add(
                FrameFeatures(
                    subRatio = subRatio,
                    bassRatio = bassRatio,
                    midRatio = midRatio,
                    highRatio = highRatio,
                    rms = rms,
                    crest = crest,
                    zcr = zcr,
                    lowEnergy = (subEnergy + bassEnergy) / count.toDouble()
                )
            )
        }

        if (frames.size < MIN_FRAMES) return null
        val clippedRatio = if (totalSamples > 0) {
            clippedSamples.toDouble() / totalSamples.toDouble()
        } else {
            0.0
        }
        val dcOffset = if (totalSamples > 0) {
            sumSamples / totalSamples.toDouble()
        } else {
            0.0
        }
        return classify(frames, clippedRatio, dcOffset)
    }

    private fun classify(
        frames: List<FrameFeatures>,
        clippedRatio: Double,
        dcOffset: Double
    ): AudioHintResult {
        val subRatio = median(frames.map { it.subRatio })
        val bassRatio = median(frames.map { it.bassRatio })
        val lowRatio = (subRatio + bassRatio).coerceIn(0.0, 1.0)
        val midRatio = median(frames.map { it.midRatio })
        val highRatio = median(frames.map { it.highRatio })
        val avgRms = mean(frames.map { it.rms })
        val avgCrest = mean(frames.map { it.crest })
        val avgZcr = mean(frames.map { it.zcr })
        val lowCv = coeffVar(frames.map { it.lowEnergy })
        val rmsCv = coeffVar(frames.map { it.rms })
        val pulse = detectLowPulses(frames)
        val pulseRate = pulse.ratePerSecond
        val pulseRegularity = pulse.regularity
        val lowSignal = avgRms < MIN_RMS || (avgRms < QUIET_RMS && pulseRate < QUIET_PULSE_RATE)
        val clippedHigh = clippedRatio >= CLIP_HIGH
        val clippedModerate = clippedRatio >= CLIP_WARN
        val dcHigh = kotlin.math.abs(dcOffset) >= DC_OFFSET_HIGH
        val dcModerate = kotlin.math.abs(dcOffset) >= DC_OFFSET_WARN

        val bassMusicScore =
            score(lowRatio, 0.45, 0.25) +
                score(subRatio, 0.2, 0.25) +
                score(pulseRate, 0.8, 1.5) +
                score(pulseRegularity, 0.35, 0.55) +
                score(lowCv, 0.25, 0.35) +
                score(avgCrest, 2.0, 2.0) -
                score(avgZcr, 0.18, 0.12)
        val voicesScore =
            score(midRatio, 0.35, 0.25) +
                score(avgZcr, 0.08, 0.12) -
                score(lowRatio, 0.5, 0.25)
        val windScore =
            score(subRatio, 0.4, 0.3) +
                score(lowRatio, 0.55, 0.25) +
                score(0.2 - pulseRate, 0.0, 0.2) +
                score(0.35 - pulseRegularity, 0.0, 0.35) +
                score(0.16 - avgZcr, 0.0, 0.16) +
                score(0.22 - lowCv, 0.0, 0.22) -
                score(avgCrest, 2.2, 1.8)
        val mechanicalScore =
            score(lowRatio, 0.4, 0.25) +
                score(bassRatio, 0.2, 0.2) +
                score(0.18 - avgZcr, 0.0, 0.18) +
                score(avgCrest, 2.4, 2.0) +
                score(0.2 - rmsCv, 0.0, 0.2)
        val broadNoiseScore =
            score(highRatio, 0.2, 0.2) +
                score(avgZcr, 0.12, 0.18) +
                score(0.35 - lowRatio, 0.0, 0.35)

        val scores = listOf(
            "bass-heavy music" to bassMusicScore,
            "voices / crowd" to voicesScore,
            "wind / rumble" to windScore,
            "mechanical / hum" to mechanicalScore,
            "broad noise" to broadNoiseScore
        ).sortedByDescending { it.second }

        val best = scores[0]
        val second = scores.getOrNull(1) ?: ("unknown" to 0.0)
        val bestScore = best.second.coerceAtLeast(0.0)
        val secondScore = second.second.coerceAtLeast(0.0)
        val delta = (bestScore - secondScore).coerceAtLeast(0.0)
        val confidence = (bestScore * 0.6 + delta * 0.4).coerceIn(0.0, 1.0)

        val label = when {
            lowSignal -> "low signal / uncertain"
            clippedHigh -> "uncertain"
            bestScore < 0.35 -> "uncertain"
            delta < 0.12 -> "mixed / uncertain"
            else -> best.first
        }
        val effectiveConfidence = when {
            lowSignal -> confidence.coerceAtMost(0.35)
            clippedHigh -> confidence.coerceAtMost(0.35)
            clippedModerate || dcHigh -> confidence * 0.6
            dcModerate -> confidence * 0.8
            else -> confidence
        }.coerceIn(0.0, 1.0)

        val sourceHint = sourceHintFor(
            label = label,
            lowRatio = lowRatio,
            midRatio = midRatio,
            highRatio = highRatio,
            crest = avgCrest,
            zcr = avgZcr,
            pulseRate = pulseRate,
            pulseRegularity = pulseRegularity,
            clippedRatio = clippedRatio,
            dcOffset = dcOffset
        )

        return AudioHintResult(
            label = label,
            confidence = effectiveConfidence,
            sourceHint = sourceHint,
            clippedRatio = clippedRatio,
            dcOffset = dcOffset,
            subRatio = subRatio,
            bassRatio = bassRatio,
            midRatio = midRatio,
            highRatio = highRatio,
            rms = avgRms,
            crest = avgCrest,
            zcr = avgZcr,
            lowPulseRate = pulseRate,
            lowPulseRegularity = pulseRegularity
        )
    }

    private fun alpha(cutoffHz: Double, dt: Double): Double {
        val rc = 1.0 / (2.0 * PI * cutoffHz)
        return dt / (rc + dt)
    }

    private fun alphaHigh(cutoffHz: Double, dt: Double): Double {
        val rc = 1.0 / (2.0 * PI * cutoffHz)
        return rc / (rc + dt)
    }

    private fun toIntLE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun toShortLE(bytes: ByteArray, offset: Int): Short {
        return (((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            (bytes[offset].toInt() and 0xFF)).toShort()
    }

    private fun downmixToMonoPcm16(pcmData: ByteArray, channels: Int): ByteArray {
        val frameBytes = channels * 2
        if (pcmData.size < frameBytes) return ByteArray(0)
        val frames = pcmData.size / frameBytes
        val out = ByteArray(frames * 2)
        var inOffset = 0
        var outOffset = 0
        for (i in 0 until frames) {
            var sum = 0
            var ch = 0
            while (ch < channels) {
                val lo = pcmData[inOffset].toInt() and 0xFF
                val hi = pcmData[inOffset + 1].toInt()
                val sample = (hi shl 8) or lo
                sum += sample.toShort().toInt()
                inOffset += 2
                ch++
            }
            val avg = (sum / channels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[outOffset] = (avg and 0xFF).toByte()
            out[outOffset + 1] = ((avg shr 8) and 0xFF).toByte()
            outOffset += 2
        }
        return out
    }

    private fun mean(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        return values.sum() / values.size
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid]
        }
    }

    private fun coeffVar(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val avg = mean(values)
        if (avg == 0.0) return 0.0
        val variance = values.sumOf { (it - avg) * (it - avg) } / values.size
        return sqrt(variance) / avg
    }

    private fun score(value: Double, center: Double, span: Double): Double {
        if (span <= 0.0) return 0.0
        val normalized = (value - center) / span
        return normalized.coerceIn(0.0, 1.0)
    }

    private data class PulseStats(
        val ratePerSecond: Double,
        val regularity: Double
    )

    private fun detectLowPulses(frames: List<FrameFeatures>): PulseStats {
        if (frames.size < 4) return PulseStats(0.0, 0.0)
        val energies = frames.map { it.lowEnergy }
        val smoothed = smoothEnergies(energies, SMOOTH_WINDOW_FRAMES)
        val med = median(smoothed)
        val mad = median(smoothed.map { abs(it - med) })
        val threshold = if (mad > 0.0) med + mad * 2.5 else med * 1.5
        val peaks = mutableListOf<Int>()
        var lastPeak = -MIN_PEAK_DISTANCE_FRAMES
        for (i in 1 until smoothed.size - 1) {
            val prev = smoothed[i - 1]
            val curr = smoothed[i]
            val next = smoothed[i + 1]
            if (curr > threshold && curr > prev && curr >= next) {
                if (i - lastPeak < MIN_PEAK_DISTANCE_FRAMES) {
                    if (peaks.isNotEmpty() && curr > smoothed[lastPeak]) {
                        peaks[peaks.lastIndex] = i
                        lastPeak = i
                    }
                    continue
                }
                peaks.add(i)
                lastPeak = i
            }
        }
        val durationSeconds = frames.size * FRAME_MS / 1000.0
        val rate = if (durationSeconds > 0.0) peaks.size / durationSeconds else 0.0
        if (peaks.size < 3) return PulseStats(rate, 0.0)
        val intervals = peaks.zipWithNext { a, b -> (b - a).toDouble() }
        val intervalCv = coeffVar(intervals)
        val regularity = (1.0 - intervalCv).coerceIn(0.0, 1.0)
        return PulseStats(rate, regularity)
    }

    private fun smoothEnergies(values: List<Double>, window: Int): List<Double> {
        if (values.isEmpty()) return values
        if (window <= 1) return values
        val radius = window / 2
        val out = DoubleArray(values.size)
        for (i in values.indices) {
            var sum = 0.0
            var count = 0
            val start = (i - radius).coerceAtLeast(0)
            val end = (i + radius).coerceAtMost(values.lastIndex)
            for (j in start..end) {
                sum += values[j]
                count++
            }
            out[i] = if (count > 0) sum / count.toDouble() else values[i]
        }
        return out.toList()
    }

    private fun sourceHintFor(
        label: String,
        lowRatio: Double,
        midRatio: Double,
        highRatio: Double,
        crest: Double,
        zcr: Double,
        pulseRate: Double,
        pulseRegularity: Double,
        clippedRatio: Double,
        dcOffset: Double
    ): String {
        return when (label) {
            "bass-heavy music" -> {
                if (lowRatio >= 0.55 && crest >= 2.0 && pulseRate >= 0.6 && pulseRegularity >= 0.35) {
                    "likely stage / PA dominated"
                } else {
                    "likely ambient / low-end dominant"
                }
            }
            "voices / crowd" -> "likely crowd / audience"
            "wind / rumble" -> "likely ambient / wind"
            "mechanical / hum" -> "likely technical / machine"
            "broad noise" -> {
                if (highRatio >= 0.22 || zcr >= 0.15) {
                    "likely ambient / site noise"
                } else {
                    "likely mixed source"
                }
            }
            "low signal / uncertain" -> "uncertain source (signal too quiet)"
            "mixed / uncertain" -> {
                if (midRatio >= 0.38 && lowRatio < 0.55) {
                    "likely crowd / audience"
                } else {
                    "mixed source"
                }
            }
            "uncertain" -> {
                if (clippedRatio >= CLIP_HIGH) {
                    "uncertain source (clipped input)"
                } else if (kotlin.math.abs(dcOffset) >= DC_OFFSET_HIGH) {
                    "uncertain source (DC offset)"
                } else if (midRatio >= 0.4 && lowRatio < 0.55) {
                    "likely crowd / audience"
                } else if (lowRatio >= 0.55 && crest >= 2.0 && pulseRate >= 0.6) {
                    "likely stage / PA dominated"
                } else {
                    "uncertain source"
                }
            }
            else -> "uncertain source"
        }
    }

    fun formatShort(result: AudioHintResult): String {
        val conf = String.format(Locale.US, "%.2f", result.confidence)
        val warnings = mutableListOf<String>()
        if (result.clippedRatio >= CLIP_WARN) warnings.add("clipped")
        if (kotlin.math.abs(result.dcOffset) >= DC_OFFSET_WARN) warnings.add("dc offset")
        if (result.label == "low signal / uncertain") warnings.add("low signal")
        val warnText = if (warnings.isNotEmpty()) {
            " (${warnings.joinToString(", ")})"
        } else {
            ""
        }
        return "${result.label} ($conf), ${result.sourceHint}$warnText"
    }

    fun formatDetailed(result: AudioHintResult): String {
        val conf = String.format(Locale.US, "%.2f", result.confidence)
        val clipped = String.format(Locale.US, "%.3f", result.clippedRatio)
        val dc = String.format(Locale.US, "%.4f", result.dcOffset)
        val sub = String.format(Locale.US, "%.2f", result.subRatio)
        val bass = String.format(Locale.US, "%.2f", result.bassRatio)
        val mid = String.format(Locale.US, "%.2f", result.midRatio)
        val high = String.format(Locale.US, "%.2f", result.highRatio)
        val rms = String.format(Locale.US, "%.4f", result.rms)
        val crest = String.format(Locale.US, "%.2f", result.crest)
        val zcr = String.format(Locale.US, "%.3f", result.zcr)
        val pulseRate = String.format(Locale.US, "%.2f", result.lowPulseRate)
        val pulseReg = String.format(Locale.US, "%.2f", result.lowPulseRegularity)
        return buildString {
            append("${result.label} ($conf)\n")
            append("${result.sourceHint}\n")
            if (result.clippedRatio >= CLIP_WARN) {
                append("clipped ratio=$clipped\n")
            }
            if (kotlin.math.abs(result.dcOffset) >= DC_OFFSET_WARN) {
                append("dc offset mean=$dc\n")
            }
            append("sub=$sub bass=$bass mid=$mid high=$high\n")
            append("rms=$rms crest=$crest zcr=$zcr\n")
            append("low pulse: rate=$pulseRate/s regularity=$pulseReg")
        }
    }

    private const val FRAME_MS = 30
    private const val MIN_FRAMES = 6
    private const val CLIP_THRESHOLD = 0.98
    private const val CLIP_WARN = 0.02
    private const val CLIP_HIGH = 0.08
    private const val MIN_RMS = 0.002
    private const val QUIET_RMS = 0.004
    private const val QUIET_PULSE_RATE = 0.3
    private const val DC_OFFSET_WARN = 0.02
    private const val DC_OFFSET_HIGH = 0.05
    private const val SMOOTH_WINDOW_FRAMES = 3
    private const val MIN_PEAK_DISTANCE_FRAMES = 6
}
