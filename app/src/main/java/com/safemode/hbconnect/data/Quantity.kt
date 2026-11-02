package com.safemode.hbconnect.data

/**
 * Homebox stores quantity as a decimal number (e.g. 2.5 m of cable), so it is kept as a Double end
 * to end. Converting it to a whole number anywhere on the save path would quietly cut 2.5 to 2.
 */

/** A quantity for display and for the form field, without a trailing ".0": 2, 2.5. */
fun formatQuantity(value: Double): String =
    if (value.isFinite()) value.toBigDecimal().stripTrailingZeros().toPlainString() else value.toString()

/** Parses a typed quantity ("2", "2.5" or "2,5"); null when blank, invalid or negative. */
fun parseQuantity(raw: String): Double? =
    raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }

/** Keeps what a quantity field may hold: digits and a single decimal separator. */
fun quantityInput(raw: String): String {
    var seenSeparator = false
    return raw.filter { c ->
        when {
            c.isDigit() -> true
            (c == '.' || c == ',') && !seenSeparator -> {
                seenSeparator = true
                true
            }
            else -> false
        }
    }
}
