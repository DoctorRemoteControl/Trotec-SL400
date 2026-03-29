package de.drremote.trotecsl400.audio

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavWriter(
    file: File,
    private val sampleRate: Int,
    private val channels: Int,
    private val bitsPerSample: Int
) : Closeable {

    private val raf = RandomAccessFile(file, "rw")
    private var dataBytesWritten: Long = 0L

    init {
        raf.setLength(0)
        writeHeader(0)
    }

    fun writePcm(bytes: ByteArray, length: Int) {
        raf.write(bytes, 0, length)
        dataBytesWritten += length.toLong()
    }

    override fun close() {
        writeHeader(dataBytesWritten)
        raf.close()
    }

    private fun writeHeader(dataSize: Long) {
        raf.seek(0)
        raf.writeBytes("RIFF")
        raf.writeIntLE((36 + dataSize).toInt())
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        raf.writeIntLE(16)
        raf.writeShortLE(1.toShort()) // PCM
        raf.writeShortLE(channels.toShort())
        raf.writeIntLE(sampleRate)
        val byteRate = sampleRate * channels * bitsPerSample / 8
        raf.writeIntLE(byteRate)
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        raf.writeShortLE(blockAlign)
        raf.writeShortLE(bitsPerSample.toShort())
        raf.writeBytes("data")
        raf.writeIntLE(dataSize.toInt())
    }

    private fun RandomAccessFile.writeIntLE(value: Int) {
        val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
        write(buffer)
    }

    private fun RandomAccessFile.writeShortLE(value: Short) {
        val buffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
        write(buffer)
    }
}
