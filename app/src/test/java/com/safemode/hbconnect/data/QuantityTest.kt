package com.safemode.hbconnect.data

import com.safemode.hbconnect.data.remote.models.EntityOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuantityTest {
    @Test
    fun formatsWithoutATrailingZero() {
        assertEquals("2", formatQuantity(2.0))
        assertEquals("2.5", formatQuantity(2.5))
        assertEquals("0.25", formatQuantity(0.25))
    }

    @Test
    fun parsesDotOrComma() {
        assertEquals(2.5, parseQuantity("2.5")!!, 0.0)
        assertEquals(2.5, parseQuantity(" 2,5 ")!!, 0.0)
        assertEquals(3.0, parseQuantity("3")!!, 0.0)
        assertNull(parseQuantity(""))
        assertNull(parseQuantity("-1"))
        assertNull(parseQuantity("abc"))
    }

    @Test
    fun formattedValueParsesBackExactly() {
        listOf(1.0, 2.5, 0.1, 12.375).forEach { assertEquals(it, parseQuantity(formatQuantity(it))!!, 0.0) }
    }

    @Test
    fun inputKeepsDigitsAndOneSeparator() {
        assertEquals("1.23", quantityInput("1.2.3a"))
        assertEquals("4,5", quantityInput("4,5."))
    }

    @Test
    fun decimalQuantitySurvivesAnUnrelatedEdit() {
        val current = EntityOut(id = "e1", name = "Cable", quantity = 2.5).toUpdate()
        assertEquals(2.5, current.quantity!!, 0.0)
        val merged = mergeEdit(current, current.copy(name = "Speaker cable"), current)
        assertEquals(2.5, merged.quantity!!, 0.0)
    }
}
