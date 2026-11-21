package com.smart.msrlab.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * RetrofitClient — singleton factory for ApiService
 *
 * Mirrors Flutter: String get apiBaseUrl => gPrefs.getString('api_url') ?? DEFAULT
 *
 * Usage:
 *   val api = RetrofitClient.create(prefsManager.getApiUrlBlocking())
 *   val result = api.searchProducts("milo")
 *
 * The baseUrl is retrieved from PreferencesManager at call time — so changing
 * it in Settings takes effect on the very next API call without restarting.
 */
object RetrofitClient {

    const val DEFAULT_BASE_URL = "https://halal-app.smartnode.my"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        // 120-second timeout for AI inference (scan-image endpoint)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Create a new ApiService instance with the given baseUrl.
     * Cheap to call — Retrofit instance is lightweight.
     * Pass a fresh baseUrl each time so Settings changes are picked up.
     */
    fun create(baseUrl: String = DEFAULT_BASE_URL): ApiService {
        // Ensure trailing slash — required by Retrofit
        val url = baseUrl.trim().trimEnd('/') + "/"
        return Retrofit.Builder()
            .baseUrl(url)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
