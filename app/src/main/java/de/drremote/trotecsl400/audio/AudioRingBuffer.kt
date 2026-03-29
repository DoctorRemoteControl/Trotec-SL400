package de.drremote.trotecsl400.audio

class AudioRingBuffer(private val capacityBytes: Int) {
    private val buffer = ByteArray(capacityBytes)
    private var writeIndex = 0
    private var filledBytes = 0

    @Synchronized
    fun write(bytes: ByteArray, length: Int) {
        if (length <= 0) return
        if (length >= capacityBytes) {
            val start = length - capacityBytes
            System.arraycopy(bytes, start, buffer, 0, capacityBytes)
            writeIndex = 0
            filledBytes = capacityBytes
            return
        }

        val firstPart = minOf(length, capacityBytes - writeIndex)
        System.arraycopy(bytes, 0, buffer, writeIndex, firstPart)
        val remaining = length - firstPart
        if (remaining > 0) {
            System.arraycopy(bytes, firstPart, buffer, 0, remaining)
        }
        writeIndex = (writeIndex + length) % capacityBytes
        filledBytes = minOf(capacityBytes, filledBytes + length)
    }

    @Synchronized
    fun snapshotLast(maxBytes: Int): ByteArray {
        val actual = minOf(maxBytes, filledBytes)
        if (actual <= 0) return ByteArray(0)
        val out = ByteArray(actual)
        val start = (writeIndex - actual + capacityBytes) % capacityBytes
        val firstPart = minOf(actual, capacityBytes - start)
        System.arraycopy(buffer, start, out, 0, firstPart)
        val remaining = actual - firstPart
        if (remaining > 0) {
            System.arraycopy(buffer, 0, out, firstPart, remaining)
        }
        return out
    }

    @Synchronized
    fun clear() {
        writeIndex = 0
        filledBytes = 0
    }

    @Synchronized
    fun filledBytes(): Int = filledBytes
}
