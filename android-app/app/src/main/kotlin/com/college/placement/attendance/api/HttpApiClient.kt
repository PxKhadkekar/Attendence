package com.college.placement.attendance.api

import com.college.placement.attendance.api.dto.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Universal HTTP API Client for Placement Attendance backend.
 * Uses standard java.net.http.HttpClient with Jackson JSON serialization.
 * Fully functional across JVM unit tests, CI pipelines, and Android runtimes.
 */
open class HttpApiClient(
    baseUrl: String = "http://localhost:8080"
) {
    protected var currentBaseUrl: String = baseUrl.trimEnd('/')
    protected var currentAuthToken: String? = null

    protected val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    protected val objectMapper: ObjectMapper = ObjectMapper().apply {
        registerModule(JavaTimeModule())
    }

    open fun updateAuthToken(token: String?) {
        this.currentAuthToken = token
    }

    open fun updateBaseUrl(url: String) {
        this.currentBaseUrl = url.trimEnd('/')
    }

    open fun login(request: LoginRequest): AuthResponse {
        val json = objectMapper.writeValueAsString(request)
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$currentBaseUrl/api/v1/auth/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()

        val response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw ApiException("Login failed (HTTP ${response.statusCode()}): ${response.body()}")
        }

        val auth = objectMapper.readValue(response.body(), AuthResponse::class.java)
        updateAuthToken(auth.token)
        return auth
    }

    open fun registerDevice(request: DeviceRegisterRequest): DeviceRegisterResponse {
        val json = objectMapper.writeValueAsString(request)
        val httpRequest = newRequestBuilder("$currentBaseUrl/api/v1/devices/register")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()

        val response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw ApiException("Device registration failed (HTTP ${response.statusCode()}): ${response.body()}")
        }

        return objectMapper.readValue(response.body(), DeviceRegisterResponse::class.java)
    }

    open fun getMyDevice(): MyDeviceResponse {
        val httpRequest = newRequestBuilder("$currentBaseUrl/api/v1/devices/my-device")
            .GET()
            .build()

        val response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw ApiException("Failed to query device status (HTTP ${response.statusCode()}): ${response.body()}")
        }

        return objectMapper.readValue(response.body(), MyDeviceResponse::class.java)
    }

    open fun requestChallenge(request: ChallengeRequest): ChallengeResponse {
        val json = objectMapper.writeValueAsString(request)
        val httpRequest = newRequestBuilder("$currentBaseUrl/api/v1/attendance/sessions")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()

        val response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw ApiException("Failed to acquire session challenge (HTTP ${response.statusCode()}): ${response.body()}")
        }

        return objectMapper.readValue(response.body(), ChallengeResponse::class.java)
    }

    open fun verifyAttendance(request: VerifyAttendanceRequest): VerifyAttendanceResponse {
        val json = objectMapper.writeValueAsString(request)
        val httpRequest = newRequestBuilder("$currentBaseUrl/api/v1/attendance/verify")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()

        val response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw ApiException("Attendance verification failed (HTTP ${response.statusCode()}): ${response.body()}")
        }

        return objectMapper.readValue(response.body(), VerifyAttendanceResponse::class.java)
    }

    private fun newRequestBuilder(uri: String): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder().uri(URI.create(uri))
        currentAuthToken?.let {
            builder.header("Authorization", "Bearer $it")
        }
        return builder
    }
}

class ApiException(message: String) : RuntimeException(message)
