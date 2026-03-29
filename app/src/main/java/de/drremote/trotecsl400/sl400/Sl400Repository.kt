package de.drremote.trotecsl400.sl400

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class Sl400Repository(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val decoder = Sl400Decoder()

    private var port: UsbSerialPort? = null
    private var readJob: Job? = null

    private val _samples = MutableSharedFlow<Sl400Sample>(extraBufferCapacity = 64)
    val samples = _samples.asSharedFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors = _errors.asSharedFlow()

    fun findCandidateDevices(): List<UsbDevice> {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        Log.i("SL400", "findCandidateDevices: ${drivers.size} driver(s)")
        drivers.forEach { d ->
            val dev = d.device
            Log.i(
                "SL400",
                "candidate vid=${dev.vendorId} pid=${dev.productId} " +
                        "interfaces=${dev.interfaceCount} class=${dev.deviceClass} " +
                        "subclass=${dev.deviceSubclass} protocol=${dev.deviceProtocol} " +
                        "driver=${d.javaClass.simpleName}"
            )
        }
        return drivers.map { it.device }
    }

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    fun open(device: UsbDevice) {
        close()
        decoder.reset()

        Log.i(
            "SL400",
            "open start vid=${device.vendorId} pid=${device.productId} " +
                    "interfaces=${device.interfaceCount} class=${device.deviceClass} " +
                    "subclass=${device.deviceSubclass} protocol=${device.deviceProtocol}"
        )

        val prober = UsbSerialProber.getDefaultProber()
        val driver = prober.probeDevice(device)
            ?: error("No USB serial driver found for device")

        Log.i("SL400", "driver=${driver.javaClass.name}, ports=${driver.ports.size}")

        val connection = usbManager.openDevice(device)
            ?: error("Failed to open USB connection")

        val p = driver.ports.firstOrNull()
            ?: error("No serial port available")

        try {
            Log.i("SL400", "port.open()")
            p.open(connection)

            Log.i("SL400", "setParameters(9600,8,1,NONE)")
            p.setParameters(
                9600,
                8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )

            Log.i("SL400", "set DTR/RTS false")
            p.dtr = false
            p.rts = false

            port = p
            Log.i("SL400", "open success, startReading()")
            startReading()
        } catch (t: Throwable) {
            Log.e("SL400", "open failed", t)
            try {
                p.close()
            } catch (_: Exception) {
            }
            throw t
        }
    }

    private fun startReading() {
        readJob?.cancel()
        val p = port ?: return

        readJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(256)
            Log.i("SL400", "read loop started")

            while (isActive) {
                try {
                    val len = p.read(buffer, 1000)
                    if (len > 0) {
                        Log.d("SL400", "read $len bytes")
                        val chunk = buffer.copyOf(len)
                        val decoded = decoder.feed(chunk)
                        decoded.forEach { _samples.tryEmit(it) }
                    }
                } catch (e: Exception) {
                    Log.e("SL400", "read failed", e)
                    _errors.tryEmit(e.message ?: "USB read error")
                    break
                }
            }

            Log.i("SL400", "read loop ended")
        }
    }

    fun close() {
        readJob?.cancel()
        readJob = null
        try {
            port?.close()
        } catch (e: Exception) {
            Log.w("SL400", "close failed", e)
        }
        port = null
        decoder.reset()
    }
}
