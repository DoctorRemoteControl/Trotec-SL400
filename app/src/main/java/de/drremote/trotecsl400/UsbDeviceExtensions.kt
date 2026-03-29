package de.drremote.trotecsl400

import android.hardware.usb.UsbDevice

fun UsbDevice.displayName(): String {
    val vid = String.format("%04X", vendorId)
    val pid = String.format("%04X", productId)
    return "VID:PID $vid:$pid | if=$interfaceCount | $deviceName"
}
