package com.smart.msrlab.data.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
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
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }

    /**
     * Gson yang selamat untuk model Kotlin.
     *
     * Masalah: Gson abaikan null-safety Kotlin. Bila API hantar `"product_name": null`,
     * Gson tetap set `null` ke dalam `val productName: String` → crash
     * (NullPointerException) bila UI panggil `.isNotBlank()`.
     *
     * Penyelesaian: JSON null → "" untuk String dan → senarai kosong untuk List,
     * supaya nilai lalai dalam model sentiasa dihormati.
     */
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapterFactory(NullSafeTypeAdapterFactory)
        .create()

    private object NullSafeTypeAdapterFactory : TypeAdapterFactory {
        override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
            val raw = type.rawType
            val isString = raw == String::class.java
            val isList = List::class.java.isAssignableFrom(raw) || raw == Collection::class.java
            if (!isString && !isList) return null

            val delegate = gson.getDelegateAdapter(this, type)
            return object : TypeAdapter<T>() {
                override fun write(out: JsonWriter, value: T?) = delegate.write(out, value)

                @Suppress("UNCHECKED_CAST")
                override fun read(reader: JsonReader): T {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                        return (if (isString) "" else ArrayList<Any>()) as T
                    }
                    return delegate.read(reader)
                }
            }
        }
    }
}
