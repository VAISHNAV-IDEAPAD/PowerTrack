package com.powertrack.app.data.remote

import com.powertrack.app.data.model.AuthRequest
import com.powertrack.app.data.model.AuthResponse
import com.powertrack.app.data.model.BatchLocationUploadRequest
import com.powertrack.app.data.model.DeviceRegisterRequest
import com.powertrack.app.data.model.DeviceRegisterResponse
import com.powertrack.app.data.model.HeartbeatRequest
import com.powertrack.app.data.model.HeartbeatResponse
import com.powertrack.app.data.model.LocationUploadResponse
import com.powertrack.app.data.model.SettingsUpdateRequest
import com.powertrack.app.data.model.SettingsUpdateResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface PowerTrackApi {

    @POST("api/auth/register")
    suspend fun register(@Body request: AuthRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    @POST("api/devices/register")
    suspend fun registerDevice(@Body request: DeviceRegisterRequest): Response<DeviceRegisterResponse>

    @POST("api/devices/{deviceId}/locations")
    suspend fun uploadLocations(
        @Path("deviceId") deviceId: String,
        @Body request: BatchLocationUploadRequest
    ): Response<LocationUploadResponse>

    @POST("api/devices/{deviceId}/heartbeat")
    suspend fun heartbeat(
        @Path("deviceId") deviceId: String,
        @Body request: HeartbeatRequest
    ): Response<HeartbeatResponse>

    @PATCH("api/devices/{deviceId}/settings")
    suspend fun updateSettings(
        @Path("deviceId") deviceId: String,
        @Body request: SettingsUpdateRequest
    ): Response<SettingsUpdateResponse>
}
