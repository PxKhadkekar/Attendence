package com.college.placement.attendance

/**
 * Build configuration constants.
 * In Android Gradle builds, AGP generates BuildConfig.java.
 * In JVM and development builds, this provides the flag controls.
 */
object BuildConfig {
    @JvmField
    var DEBUG: Boolean = true

    const val APPLICATION_ID: String = "com.college.placement.attendance"
    const val BUILD_TYPE: String = "debug"
    const val VERSION_CODE: Int = 1
    const val VERSION_NAME: String = "1.0.0"
    const val COLLEGE_BLE_UUID: String = "0000FD5A-0000-1000-8000-00805F9B34FB"
    const val DEFAULT_BASE_URL: String = "http://10.0.2.2:8080/"
}
