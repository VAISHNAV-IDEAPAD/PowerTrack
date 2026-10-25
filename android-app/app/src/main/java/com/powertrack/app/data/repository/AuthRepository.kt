package com.powertrack.app.data.repository

import android.content.Context
import com.powertrack.app.data.local.SessionManager
import com.powertrack.app.data.model.AuthRequest
import com.powertrack.app.data.model.AuthResponse
import com.powertrack.app.data.model.DeviceDto
import com.powertrack.app.data.model.DeviceRegisterRequest
import com.powertrack.app.data.remote.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class AuthRepository(private val context: Context) {

    private val sessionManager = SessionManager(context)
    private val networkClient = NetworkClient.getInstance(context)

    suspend fun login(email: String, password: String): Result<AuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = networkClient.api.login(AuthRequest(email, password))
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    sessionManager.saveLogin(body.token, body.user.id, body.user.email)
                    Result.success(body)
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Login failed with code ${response.code()}"
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun register(email: String, password: String): Result<AuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = networkClient.api.register(AuthRequest(email, password))
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    sessionManager.saveLogin(body.token, body.user.id, body.user.email)
                    Result.success(body)
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Registration failed with code ${response.code()}"
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun ensureDeviceRegistered(): Result<DeviceDto> =
        withContext(Dispatchers.IO) {
            try {
                val currentDeviceId = sessionManager.deviceId ?: run {
                    val newId = UUID.randomUUID().toString()
                    sessionManager.deviceId = newId
                    newId
                }

                val req = DeviceRegisterRequest(
                    deviceName = sessionManager.deviceName,
                    platform = "android",
                    deviceId = currentDeviceId
                )

                val response = networkClient.api.registerDevice(req)
                if (response.isSuccessful && response.body() != null) {
                    val device = response.body()!!.device
                    sessionManager.deviceId = device.id
                    Result.success(device)
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Device registration failed: ${response.code()}"
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun logout() {
        sessionManager.clearSession()
    }
}
