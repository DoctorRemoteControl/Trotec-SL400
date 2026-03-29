package de.drremote.trotecsl400.sl400

data class Sl400Sample(
    val timestampMs: Long,
    val db: Double,
    val rawTenths: Int,
    val aux06Hex: String?,
    val tags: List<Int>
)
