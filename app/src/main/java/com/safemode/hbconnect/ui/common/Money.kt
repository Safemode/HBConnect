package com.safemode.hbconnect.ui.common

import com.safemode.hbconnect.data.remote.models.Currency

/**
 * Formats a monetary [amount] using the group's [currency] when known: its symbol and decimal
 * count. Falls back to a plain 2-decimal number (trimming a whole-number ".00") when no currency
 * has been resolved yet, matching the app's previous behavior.
 */
fun formatMoney(amount: Double, currency: Currency?): String {
    if (currency == null) {
        return if (amount % 1.0 == 0.0) amount.toLong().toString() else "%,.2f".format(amount)
    }
    val decimals = currency.decimals.coerceIn(0, 4)
    val number = "%,.${decimals}f".format(amount)
    val symbol = currency.symbol?.ifBlank { null }
    val code = currency.code?.ifBlank { null }
    return when {
        symbol != null -> "$symbol$number"
        code != null -> "$number $code"
        else -> number
    }
}
