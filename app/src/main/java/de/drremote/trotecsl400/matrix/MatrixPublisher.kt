package de.drremote.trotecsl400.matrix

import de.drremote.trotecsl400.sl400.Sl400Sample
import de.drremote.trotecsl400.sl400.AcousticMetrics
import de.drremote.trotecsl400.alert.AlertConfig
import java.io.File

interface MatrixPublisher {
    suspend fun sendSample(config: MatrixConfig, sample: Sl400Sample)
    suspend fun sendAlert(
        config: MatrixConfig,
        sample: Sl400Sample,
        metrics: AcousticMetrics,
        alertConfig: AlertConfig,
        audioHint: String? = null
    )
    suspend fun uploadMedia(
        config: MatrixConfig,
        file: File,
        mimeType: String,
        fileName: String
    ): MxcUploadResult
    suspend fun sendAudioClip(
        config: MatrixConfig,
        roomId: String,
        file: File,
        caption: String,
        fileName: String = file.name
    ): MxcUploadResult
    suspend fun sendAudioByMxcUrl(
        config: MatrixConfig,
        roomId: String,
        mxcUrl: String,
        caption: String,
        mimeType: String? = null,
        sizeBytes: Long? = null
    )
    suspend fun sendFile(
        config: MatrixConfig,
        roomId: String,
        file: File,
        body: String,
        mimeType: String,
        fileName: String = file.name
    ): MxcUploadResult
    suspend fun sendImage(
        config: MatrixConfig,
        roomId: String,
        file: File,
        body: String,
        mimeType: String,
        width: Int,
        height: Int,
        fileName: String = file.name
    ): MxcUploadResult
    suspend fun sendTestMessage(config: MatrixConfig)
    suspend fun sendText(config: MatrixConfig, roomId: String, body: String)
}
