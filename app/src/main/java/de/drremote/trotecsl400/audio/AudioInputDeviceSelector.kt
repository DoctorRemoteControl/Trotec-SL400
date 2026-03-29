package de.drremote.trotecsl400.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

class AudioInputDeviceSelector(private val context: Context) {

    fun findPreferredInput(): AudioDeviceInfo? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        return inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
            ?: inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
            ?: inputs.firstOrNull()
    }

    fun describe(device: AudioDeviceInfo?): String {
        if (device == null) return "None"
        val type = when (device.type) {
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Mic"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
            else -> "Other"
        }
        val name = device.productName?.toString()?.ifBlank { "Unknown" } ?: "Unknown"
        return "$type • $name"
    }
}
