package com.college.placement.attendance

import com.college.placement.attendance.api.HttpApiClient
import com.college.placement.attendance.ble.AndroidBleStationScanner
import com.college.placement.attendance.ble.BleStationScanner
import com.college.placement.attendance.ble.MockBleStationScanner
import com.college.placement.attendance.crypto.AndroidKeystoreCryptoManager
import com.college.placement.attendance.crypto.CryptoManager
import com.college.placement.attendance.repository.AuthRepository
import com.college.placement.attendance.repository.AttendanceRepository
import com.college.placement.attendance.repository.DeviceRepository

/**
 * Application context container holding zero-trust attendance dependencies.
 */
class AttendanceApplication {

    companion object {
        val apiClient: HttpApiClient by lazy {
            HttpApiClient(BuildConfig.DEFAULT_BASE_URL)
        }

        val cryptoManager: CryptoManager by lazy {
            AndroidKeystoreCryptoManager()
        }

        /**
         * BLE scanner factory:
         * In debug mode, if dev mock is requested, allows MockBleStationScanner.
         * In production/release builds, MockBleStationScanner is strictly guarded by BuildConfig.DEBUG
         * and cannot be instantiated without throwing SecurityException.
         */
        fun createBleScanner(useDevMock: Boolean = false): BleStationScanner {
            return if (useDevMock && BuildConfig.DEBUG) {
                MockBleStationScanner()
            } else {
                AndroidBleStationScanner()
            }
        }

        val authRepository: AuthRepository by lazy {
            AuthRepository(apiClient)
        }

        val deviceRepository: DeviceRepository by lazy {
            DeviceRepository(apiClient, cryptoManager)
        }

        val attendanceRepository: AttendanceRepository by lazy {
            AttendanceRepository(apiClient, cryptoManager)
        }
    }
}
