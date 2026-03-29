package de.drremote.trotecsl400.matrix

data class MatrixConfig(
    val homeserverBaseUrl: String = "",
    val accessToken: String = "",
    val roomId: String = "",
    val enabled: Boolean = false
)
