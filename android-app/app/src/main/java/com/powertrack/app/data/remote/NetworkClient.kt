package com.powertrack.app.data.remote

import android.content.Context
import com.powertrack.app.data.local.SessionManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class NetworkClient private constructor(context: Context) {

    private val sessionManager = SessionManager(context)
    private var currentBaseUrl = ""
    private var cachedApi: PowerTrackApi? = null

    val api: PowerTrackApi
        get() {
            val targetUrl = sessionManager.serverUrl.let {
                if (it.endsWith("/")) it else "$it/"
            }

            if (cachedApi == null || currentBaseUrl != targetUrl) {
                currentBaseUrl = targetUrl
                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }

                val okHttpClient = OkHttpClient.Builder()
                    .addInterceptor(AuthInterceptor(sessionManager))
                    .addInterceptor(logging)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(targetUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                cachedApi = retrofit.create(PowerTrackApi::class.java)
            }
            return cachedApi!!
        }

    companion object {
        @Volatile
        private var INSTANCE: NetworkClient? = null

        fun getInstance(context: Context): NetworkClient {
            return INSTANCE ?: synchronized(this) {
                val instance = NetworkClient(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
