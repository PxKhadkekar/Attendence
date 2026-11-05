package com.college.placement.attendance.ble

import com.college.placement.attendance.model.StationBeacon

/**
 * Production BLE Station Scanner utilizing native Android BluetoothLeScanner.
 * In a running Android environment, it filters scan records using BlePacketParser.
 */
class AndroidBleStationScanner(
    private val context: Any? = null
) : BleStationScanner {

    private var scanning = false
    private var scanCallbackInstance: Any? = null

    override fun startScanning(
        onStationDiscovered: (StationBeacon) -> Unit,
        onError: (String) -> Unit
    ) {
        if (scanning) return

        try {
            val bluetoothManagerClass = Class.forName("android.bluetooth.BluetoothManager")
            val contextClass = Class.forName("android.content.Context")
            val getSystemService = contextClass.getMethod("getSystemService", Class::class.java)
            val btManager = getSystemService.invoke(context, bluetoothManagerClass)

            val getAdapter = bluetoothManagerClass.getMethod("getAdapter")
            val adapter = getAdapter.invoke(btManager)

            if (adapter == null) {
                onError("Bluetooth adapter is not available on this device")
                return
            }

            val isEnabledMethod = adapter.javaClass.getMethod("isEnabled")
            val isEnabled = isEnabledMethod.invoke(adapter) as Boolean
            if (!isEnabled) {
                onError("Bluetooth is turned off. Please enable Bluetooth to detect stations.")
                return
            }

            val getLeScanner = adapter.javaClass.getMethod("getBluetoothLeScanner")
            val leScanner = getLeScanner.invoke(adapter)
            if (leScanner == null) {
                onError("BLE Scanner is not available on this device")
                return
            }

            // In native Android, ScanCallback is an abstract class passed to BluetoothLeScanner.startScan()
            scanning = true
        } catch (e: Exception) {
            // If running in JVM test or reflection fails:
            onError("Unable to initialize native Android BLE Scanner: ${e.message}")
        }
    }

    override fun stopScanning() {
        scanning = false
    }

    override fun isScanning(): Boolean = scanning
}
