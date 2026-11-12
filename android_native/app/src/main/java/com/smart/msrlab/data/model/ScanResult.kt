package com.smart.msrlab.data.model

import com.google.gson.annotations.SerializedName

/**
 * ScanResult — dikembalikan oleh POST /scan-image
 *
 * Sepadan dengan Flutter:
 *   class ScanResult { ocrTexts, matchedProduct, matchedCategory, confidence,
 *                       detectionMethod, halalRecords, visionResult }
 */
data class ScanResult(
    @SerializedName("ocr_texts")        val ocrTexts: List<String> = emptyList(),
    @SerializedName("matched_product")  val matchedProduct: String? = null,
    @SerializedName("matched_category") val matchedCategory: String? = null,
    @SerializedName("confidence")       val confidence: Double? = null,
    @SerializedName("detection_method") val detectionMethod: String = "none",
    @SerializedName("halal_records")    val halalRecords: List<HalalProduct> = emptyList(),
    @SerializedName("vision_result")    val visionResult: VisionResult? = null,
) {
    val confidencePct: Int get() = ((confidence ?: 0.0) * 100).toInt()
    val hasHalalRecords: Boolean get() = halalRecords.isNotEmpty()
    val isVision: Boolean get() = detectionMethod == "vision"
    val isOcr: Boolean get() = detectionMethod == "ocr"
}

/**
 * VisionResult — nested dalam ScanResult.vision_result
 * Qwen3-VL mengenal pasti brand dan nama produk dari gambar.
 */
data class VisionResult(
    @SerializedName("brand")   val brand: String? = null,
    @SerializedName("product") val product: String? = null,
)

/**
 * LogoScanResult — dikembalikan oleh POST /scan-logo
 *
 * Sepadan dengan Flutter logo_scan_screen.dart:
 *   _visionResult, _extractedTexts, _matches, _isRecognised, _detectionMethod
 */
data class LogoScanResult(
    @SerializedName("vision_result")         val visionResult: Map<String, Any?>? = null,
    @SerializedName("extracted_texts")       val extractedTexts: List<String> = emptyList(),
    @SerializedName("matches")               val matches: List<LogoMatch> = emptyList(),
    @SerializedName("is_recognised_by_jakim") val isRecognisedByJakim: Boolean? = null,
    @SerializedName("detection_method")      val detectionMethod: String = "",
)

/**
 * LogoMatch — satu badan pensijilan yang dikenali oleh JAKIM
 * Sepadan dengan match object dalam /scan-logo response.
 */
data class LogoMatch(
    @SerializedName("organization_name") val organizationName: String = "",
    @SerializedName("abbreviation")      val abbreviation: String = "",
    @SerializedName("country")           val country: String = "",
    @SerializedName("email")             val email: String = "",
    @SerializedName("website")           val website: String = "",
    @SerializedName("phone")             val phone: String = "",
)

/**
 * PremiseScanResult — dikembalikan oleh POST /scan-premises (drone halal scan)
 */
data class PremiseScanResult(
    @SerializedName("matches")        val matches: List<HalalProduct> = emptyList(),
    @SerializedName("halal_records")  val halalRecords: List<HalalProduct> = emptyList(),
    @SerializedName("vision_result")  val visionResult: VisionResult? = null,
    @SerializedName("vision_texts")   val visionTexts: List<String> = emptyList(),
    @SerializedName("ocr_texts")      val ocrTexts: List<String> = emptyList(),
) {
    /** Ambil semua matches — API guna 'matches' atau 'halal_records' */
    val allMatches: List<HalalProduct> get() = matches.ifEmpty { halalRecords }

    /** Semua teks yang dikesan (gabung vision + ocr, buang duplikat) */
    val allDetectedTexts: List<String> get() {
        val vt = visionTexts.toMutableList()
        visionResult?.brand?.let { if (it.isNotBlank()) vt.add(it) }
        visionResult?.product?.let { if (it.isNotBlank()) vt.add(it) }
        return (vt + ocrTexts).map { it.toString() }.distinct().take(5)
    }
}
