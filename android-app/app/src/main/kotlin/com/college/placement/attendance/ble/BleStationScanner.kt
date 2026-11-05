package com.college.placement.attendance.ble

import com.college.placement.attendance.model.StationBeacon

/**
 * Interface for scanning physical BLE station beacons located inside placement halls.
 */
interface BleStationScanner {
    /**
     * Starts continuous BLE scanning for college placement beacons.
     * Invokes [onStationDiscovered] whenever a beacon matching the college UUID is seen.
     */
    fun startScanning(
        onStationDiscovered: (StationBeacon) -> Unit,
        onError: (String) -> Unit
    )

    /**
     * Stops BLE scanning to conserve device battery.
     */
    fun stopScanning()

    /**
     * Checks if scanning is currently active.
     */
    fun isScanning(): Boolean
}
