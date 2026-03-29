package de.drremote.trotecsl400.sl400

class Sl400Decoder {

    private enum class State {
        SEEK_MARKER,
        READ_TAG,
        READ_PAYLOAD
    }

    private var state = State.SEEK_MARKER
    private var currentTag = 0
    private var expectedPayloadLen = 0
    private val payload = ArrayList<Byte>(4)

    private var measurementRawTenths: Int? = null
    private var aux06Hex: String? = null
    private val seenTags = mutableListOf<Int>()

    fun reset() {
        state = State.SEEK_MARKER
        currentTag = 0
        expectedPayloadLen = 0
        payload.clear()
        resetSample()
    }

    fun feed(bytes: ByteArray): List<Sl400Sample> {
        val out = mutableListOf<Sl400Sample>()

        for (b in bytes) {
            val ub = b.toInt() and 0xFF

            when (state) {
                State.SEEK_MARKER -> {
                    if (ub == 0xA5) {
                        state = State.READ_TAG
                    }
                }

                State.READ_TAG -> {
                    currentTag = ub
                    val len = payloadLengthForTag(ub)
                    if (len < 0) {
                        // unknownTagHandling = resync_to_next_marker
                        state = State.SEEK_MARKER
                    } else if (len == 0) {
                        handleTag(currentTag, byteArrayOf(), out)
                        state = State.SEEK_MARKER
                    } else {
                        payload.clear()
                        expectedPayloadLen = len
                        state = State.READ_PAYLOAD
                    }
                }

                State.READ_PAYLOAD -> {
                    payload.add(b)
                    if (payload.size >= expectedPayloadLen) {
                        handleTag(currentTag, payload.toByteArray(), out)
                        payload.clear()
                        state = State.SEEK_MARKER
                    }
                }
            }
        }

        return out
    }

    private fun payloadLengthForTag(tag: Int): Int = when (tag) {
        0x0D -> 2
        0x06 -> 3
        0x1B -> 1
        0x0B -> 1
        0x00 -> 0
        0x02 -> 0
        0x0C -> 0
        0x0E -> 0
        0x19 -> 0
        0x1A -> 0
        0x1F -> 0
        0x4B -> 0
        else -> -1
    }

    private fun handleTag(tag: Int, payload: ByteArray, out: MutableList<Sl400Sample>) {
        seenTags += tag

        when (tag) {
            0x0D -> {
                val b1 = payload[0].toInt() and 0xFF
                val b2 = payload[1].toInt() and 0xFF
                measurementRawTenths = decodeMeasurementTenths(b1, b2)
            }

            0x06 -> {
                aux06Hex = payload.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            }

            0x00 -> {
                val raw = measurementRawTenths
                if (raw != null) {
                    out += Sl400Sample(
                        timestampMs = System.currentTimeMillis(),
                        db = raw / 10.0,
                        rawTenths = raw,
                        aux06Hex = aux06Hex,
                        tags = seenTags.toList()
                    )
                }
                resetSample()
            }

            else -> {
                // nur tag merken
            }
        }
    }

    private fun decodeMeasurementTenths(byte1: Int, byte2: Int): Int {
        val hundreds = byte1 and 0x0F
        val tens = (byte2 shr 4) and 0x0F
        val ones = byte2 and 0x0F
        return (hundreds * 100) + (tens * 10) + ones
    }

    private fun resetSample() {
        measurementRawTenths = null
        aux06Hex = null
        seenTags.clear()
    }
}
