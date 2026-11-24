package com.example.android.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    // Default to current Wi-Fi IP (10.14.138.1). Can be updated dynamically or set to 127.0.0.1 for USB reverse
    private var baseUrl: String = "http://10.0.2.2:8000/"
    private var _service: ApiService? = null

    fun setBaseUrl(newUrl: String) {
        val clean = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        baseUrl = clean
        _service = null // forces Retrofit to rebuild with new URL
    }

    fun getBaseUrl(): String = baseUrl

    private var token: String? = null
    var currentUser: com.example.android.data.model.UserResponse? = null

    fun setToken(newToken: String) { token = newToken }
    fun clearToken() {
        token = null
        currentUser = null
        com.example.android.data.model.SessionState.startNewSession()
    }
    fun getToken(): String? = token

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .addInterceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    token?.let { addHeader("Authorization", "Bearer $it") }
                }.build()
                chain.proceed(request)
            }
            .build()
    }

    val service: ApiService
        get() {
            if (_service == null) {
                _service = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService::class.java)
            }
            return _service!!
        }
}
