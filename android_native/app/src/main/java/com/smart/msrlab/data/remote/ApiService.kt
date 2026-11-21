package com.smart.msrlab.data.remote

import com.smart.msrlab.data.model.*
import okhttp3.MultipartBody
import retrofit2.http.*

/**
 * ApiService — Retrofit interface for all backend endpoints
 *
 * Base URL is set dynamically in RetrofitClient from DataStore (user-configurable).
 * Default: https://halal-jakim-backend-vfhol5-85579f-159-65-136-40.sslip.io
 *
 * Mirrors Flutter ApiService in main.dart + logo_scan_screen.dart + drone_screen.dart inline calls.
 */
interface ApiService {

    // ── GET /search?q=...&limit=50 ────────────────────────────────────────────
    @GET("search")
    suspend fun searchProducts(
        @Query("q") query: String,
        @Query("limit") limit: Int = 50,
    ): SearchResponse

    // ── GET /search-grouped?q=...&limit=100 ──────────────────────────────────
    @GET("search-grouped")
    suspend fun searchGrouped(
        @Query("q") query: String,
        @Query("limit") limit: Int = 100,
    ): SearchGroupedResponse

    // ── GET /company-products?company=...&q=... ───────────────────────────────
    @GET("company-products")
    suspend fun getCompanyProducts(
        @Query("company") company: String,
        @Query("q") query: String = "",
    ): CompanyProductsResponse

    // ── POST /scan-image (multipart file upload) ──────────────────────────────
    // 120 second timeout — EfficientNet + Qwen3-VL inference on GPU server
    @Multipart
    @POST("scan-image")
    suspend fun scanImage(
        @Part file: MultipartBody.Part,
    ): ScanResult

    // ── POST /scan-logo (multipart file upload) ───────────────────────────────
    @Multipart
    @POST("scan-logo")
    suspend fun scanLogo(
        @Part file: MultipartBody.Part,
    ): LogoScanResult

    // ── POST /scan-premises (drone halal scan, multipart) ─────────────────────
    @Multipart
    @POST("scan-premises")
    suspend fun scanPremises(
        @Part file: MultipartBody.Part,
    ): PremiseScanResult
}
