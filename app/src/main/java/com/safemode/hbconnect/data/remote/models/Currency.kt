package com.safemode.hbconnect.data.remote.models

/** A supported currency (GET /api/v1/currencies). */
data class Currency(
    val name: String? = null,
    val code: String? = null,
    val local: String? = null,
    val symbol: String? = null,
    val decimals: Int = 2,
)
