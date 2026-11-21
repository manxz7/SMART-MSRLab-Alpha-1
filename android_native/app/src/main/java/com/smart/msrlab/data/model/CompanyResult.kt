package com.smart.msrlab.data.model

import com.google.gson.annotations.SerializedName

/**
 * CompanyResult — satu syarikat dalam hasil carian grouped
 *
 * Sepadan dengan Flutter main.dart:
 *   class CompanyResult { company, productCount, popularityScore, categories, products }
 *
 * JSON dari GET /search-grouped:
 *   { "company": "Nestle", "product_count": 45, "popularity_score": 12, "categories": [...], "products": [...] }
 */
data class CompanyResult(
    @SerializedName("company")          val company: String = "",
    @SerializedName("product_count")    val productCount: Int = 0,
    @SerializedName("popularity_score") val popularityScore: Int = 0,
    @SerializedName("categories")       val categories: List<String> = emptyList(),
    @SerializedName("products")         val products: List<HalalProduct> = emptyList(),
) {
    val isPopular: Boolean get() = popularityScore > 0
}

/**
 * SearchResponse — wrapper untuk GET /search
 */
data class SearchResponse(
    @SerializedName("results") val results: List<HalalProduct> = emptyList(),
    @SerializedName("total")   val total: Int = 0,
)

/**
 * SearchGroupedResponse — wrapper untuk GET /search-grouped
 */
data class SearchGroupedResponse(
    @SerializedName("companies") val companies: List<CompanyResult> = emptyList(),
    @SerializedName("total")     val total: Int = 0,
)

/**
 * CompanyProductsResponse — wrapper untuk GET /company-products
 */
data class CompanyProductsResponse(
    @SerializedName("products") val products: List<HalalProduct> = emptyList(),
    @SerializedName("total")    val total: Int = 0,
)
