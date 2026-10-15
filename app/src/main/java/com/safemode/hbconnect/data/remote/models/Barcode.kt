package com.safemode.hbconnect.data.remote.models

/**
 * One match from GET /api/v1/products/search-from-barcode. Homebox aggregates several UPC/EAN
 * databases, so a lookup may return multiple candidates. Only the fields the app prefills are
 * modelled; unknown keys (search engine, country, etc.) are ignored.
 */
data class BarcodeProduct(
    val modelNumber: String? = null,
    val manufacturer: String? = null,
    val barcode: String? = null,
    val imageURL: String? = null,
    val item: BarcodeItem? = null,
) {
    /** A readable label for a candidate when the user must pick between several matches. */
    val label: String
        get() = item?.name?.ifBlank { null }
            ?: manufacturer?.ifBlank { null }
            ?: barcode
            ?: "Product"
}

/** The prefilled item carried by a [BarcodeProduct] (Homebox's EntityCreate, trimmed). */
data class BarcodeItem(
    val name: String? = null,
    val description: String? = null,
)
