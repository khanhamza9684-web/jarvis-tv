package com.tvpad

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors

enum class LinkStatus { IDLE, STARTING, READY, CONNECTING, CONNECTED, UNSUPPORTED, NO_PERMISSION }

/** Everything the HUD shows about the link. */
data class LinkInfo(
    val status: LinkStatus = LinkStatus.IDLE,
    val hostName: String? = null,
    val reportsPerSec: Int = 0,
    val totalReports: Long = 0,
    val lastSendMs: Float = 0f,
    val message: String? = null,
)

/**
 * Registers the phone as a Bluetooth HID "gamepad" and streams input reports
 * to the connected host (Google TV / Android TV / any HID-capable host).
 */
@SuppressLint("MissingPermission") // permissions are requested in MainActivity before start()
class HidGamepad(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val _info = MutableStateFlow(LinkInfo())
    val info: StateFlow<LinkInfo> = _info.asStateFlow()

    private var windowStart = SystemClock.elapsedRealtime()
    private var windowCount = 0
    private var lastReport: ByteArray? = null

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            _info.update {
                it.copy(
                    status = if (registered) LinkStatus.READY else LinkStatus.STARTING,
                    message = if (registered) "Ready — pair or connect from the TV" else null,
                )
            }
            if (registered && pluggedDevice != null) connect(pluggedDevice)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    _info.update {
                        it.copy(status = LinkStatus.CONNECTED, hostName = device?.name ?: device?.address, message = null)
                    }
                }
                BluetoothProfile.STATE_CONNECTING ->
                    _info.update { it.copy(status = LinkStatus.CONNECTING, hostName = device?.name) }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    host = null
                    _info.update { it.copy(status = LinkStatus.READY, hostName = null, message = "Disconnected") }
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            // Host polled for a report: answer with the latest one.
            val data = lastReport ?: GamepadState().toReport()
            hid?.replyReport(device, type, id, data)
        }
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            val h = proxy as BluetoothHidDevice
            hid = h
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "TV Pad",
                "Virtual Gamepad",
                "TVPad",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                HidDescriptor.GAMEPAD,
            )
            val ok = h.registerApp(sdp, null, null, executor, callback)
            if (!ok) _info.update { it.copy(status = LinkStatus.UNSUPPORTED, message = "registerApp failed") }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hid = null
                _info.update { LinkInfo(status = LinkStatus.IDLE, message = "Bluetooth HID service lost") }
            }
        }
    }

    fun start() {
        val a = adapter
        if (a == null || !a.isEnabled) {
            _info.update { it.copy(status = LinkStatus.UNSUPPORTED, message = "Bluetooth is off or unavailable") }
            return
        }
        _info.update { it.copy(status = LinkStatus.STARTING, message = "Starting HID service…") }
        val requested = a.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
        if (!requested) {
            _info.update { it.copy(status = LinkStatus.UNSUPPORTED, message = "This phone does not support Bluetooth HID device mode") }
        }
    }

    fun stop() {
        try {
            hid?.unregisterApp()
        } catch (_: Exception) {
        }
        hid?.let { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        hid = null
        host = null
        _info.update { LinkInfo() }
    }

    /** Devices already paired with this phone (the TV should appear here after pairing). */
    fun bondedDevices(): List<BluetoothDevice> =
        adapter?.bondedDevices?.sortedBy { it.name ?: it.address } ?: emptyList()

    fun connect(device: BluetoothDevice) {
        _info.update { it.copy(status = LinkStatus.CONNECTING, hostName = device.name, message = null) }
        hid?.connect(device)
    }

    fun disconnect() {
        host?.let { hid?.disconnect(it) }
    }

    /** Sends one report if connected. Safe to call from any thread. */
    fun send(state: GamepadState) {
        val h = hid ?: return
        val d = host ?: return
        val report = state.toReport()
        lastReport = report
        val t0 = SystemClock.elapsedRealtimeNanos()
        val ok = h.sendReport(d, HidDescriptor.REPORT_ID, report)
        val dtMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000f
        if (!ok) return

        windowCount++
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - windowStart
        val rate = if (elapsed >= 1000) (windowCount * 1000f / elapsed).toInt() else null
        if (rate != null) {
            windowStart = now
            windowCount = 0
        }
        _info.update {
            it.copy(
                totalReports = it.totalReports + 1,
                reportsPerSec = rate ?: it.reportsPerSec,
                lastSendMs = dtMs,
            )
        }
    }

    fun reportNoPermission() {
        _info.update { it.copy(status = LinkStatus.NO_PERMISSION, message = "Allow Nearby devices permission, then reopen the app") }
    }
}

object HidDescriptor {
    const val REPORT_ID = 1

    /**
     * Gamepad: 16 buttons, 8-way hat, 4 stick axes (X, Y, Z, Rz), 2 trigger axes (Rx, Ry).
     * Report payload = 2 + 1 + 4 + 2 = 9 bytes (matches GamepadState.REPORT_SIZE).
     */
    val GAMEPAD: ByteArray = intArrayOf(
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x05,             // Usage (Game Pad)
        0xA1, 0x01,             // Collection (Application)
        0x85, REPORT_ID,        //   Report ID (1)

        // 16 buttons
        0x05, 0x09,             //   Usage Page (Button)
        0x19, 0x01,             //   Usage Minimum (1)
        0x29, 0x10,             //   Usage Maximum (16)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x01,             //   Logical Maximum (1)
        0x75, 0x01,             //   Report Size (1)
        0x95, 0x10,             //   Report Count (16)
        0x81, 0x02,             //   Input (Data,Var,Abs)

        // Hat switch (4 bits, 1..8, 0 = null)
        0x05, 0x01,             //   Usage Page (Generic Desktop)
        0x09, 0x39,             //   Usage (Hat switch)
        0x15, 0x01,             //   Logical Minimum (1)
        0x25, 0x08,             //   Logical Maximum (8)
        0x35, 0x00,             //   Physical Minimum (0)
        0x46, 0x3B, 0x01,       //   Physical Maximum (315)
        0x65, 0x14,             //   Unit (Degrees)
        0x75, 0x04,             //   Report Size (4)
        0x95, 0x01,             //   Report Count (1)
        0x81, 0x42,             //   Input (Data,Var,Abs,Null state)

        // 4 bits padding
        0x75, 0x04,
        0x95, 0x01,
        0x81, 0x03,             //   Input (Const,Var,Abs)

        // Sticks: X, Y, Z, Rz
        0x05, 0x01,
        0x09, 0x30,             //   Usage (X)
        0x09, 0x31,             //   Usage (Y)
        0x09, 0x32,             //   Usage (Z)
        0x09, 0x35,             //   Usage (Rz)
        0x15, 0x00,
        0x26, 0xFF, 0x00,       //   Logical Maximum (255)
        0x35, 0x00,
        0x45, 0x00,             //   Physical range reset
        0x65, 0x00,             //   Unit (none) — clear the degrees unit set for the hat
        0x75, 0x08,
        0x95, 0x04,
        0x81, 0x02,

        // Triggers: Rx, Ry
        0x09, 0x33,             //   Usage (Rx)
        0x09, 0x34,             //   Usage (Ry)
        0x15, 0x00,
        0x26, 0xFF, 0x00,
        0x75, 0x08,
        0x95, 0x02,
        0x81, 0x02,

        0xC0,                   // End Collection
    ).map { it.toByte() }.toByteArray()
}
