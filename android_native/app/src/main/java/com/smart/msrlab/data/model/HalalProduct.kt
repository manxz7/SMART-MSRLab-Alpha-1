package com.smart.msrlab.data.model

import com.google.gson.annotations.SerializedName

/**
 * HalalProduct — satu rekod sijil halal dari pangkalan data JAKIM
 *
 * Sepadan dengan data class dalam Flutter main.dart:
 *   class HalalProduct { id, category, compCode, company, brand, productName, address, expiry }
 *
 * JSON dari API:
 *   { "id": 1, "company": "Nestle", "product_name": "MILO", "category": "F&B", ... }
 */
data class HalalProduct(
    @SerializedName("id")           val id: Int = 0,
    @SerializedName("category")     val category: String = "",
    @SerializedName("comp_code")    val compCode: String = "",
    @SerializedName("company")      val company: String = "",
    @SerializedName("brand")        val brand: String = "",
    @SerializedName("product_name") val productName: String = "",
    @SerializedName("address")      val address: String = "",
    @SerializedName("expiry")       val expiry: String = "",
) {
    /** Returns true jika tarikh luput sudah lepas. Format: "DD/MM/YYYY" */
    fun isExpired(): Boolean {
        return try {
            val parts = expiry.split("/")
            if (parts.size != 3) return false
            val d = parts[0].toInt()
            val m = parts[1].toInt()
            val y = parts[2].toInt()
            val exp = java.util.Calendar.getInstance().apply {
                set(y, m - 1, d, 23, 59, 59)
            }.time
            exp.before(java.util.Date())
        } catch (_: Exception) {
            false
        }
    }
}
