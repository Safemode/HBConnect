package com.safemode.hbconnect.ui.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsePriceTest {
    private fun assertPrice(expected: Double, raw: String) =
        assertEquals(expected, parsePrice(raw)!!, 0.0001)

    @Test
    fun dotDecimal() {
        assertPrice(12.5, "12.50")
        assertPrice(1234.0, "1234")
    }

    @Test
    fun commaDecimal() {
        assertPrice(12.5, "12,50")
        assertPrice(1.5, "1,5")
    }

    @Test
    fun commaThousands() {
        assertPrice(1234.0, "1,234")
        assertPrice(1234567.0, "1,234,567")
    }

    @Test
    fun mixedSeparators() {
        assertPrice(1234.56, "1,234.56")
        assertPrice(1234.56, "1.234,56")
    }

    @Test
    fun invalid() {
        assertNull(parsePrice(""))
        assertNull(parsePrice("1.2.3"))
        assertNull(parsePrice(","))
    }
}
