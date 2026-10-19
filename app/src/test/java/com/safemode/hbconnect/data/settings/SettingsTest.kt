package com.safemode.hbconnect.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardLayoutTest {
    @Test
    fun roundTrips() {
        val layout = defaultDashboardLayout().reversed().map { it.copy(visible = it.id != DashboardTileId.TAGS) }
        assertEquals(layout, decodeDashboardLayout(encodeDashboardLayout(layout)))
    }

    @Test
    fun blankOrGarbageFallsBackToDefault() {
        assertEquals(defaultDashboardLayout(), decodeDashboardLayout(null))
        assertEquals(defaultDashboardLayout(), decodeDashboardLayout("not a layout"))
    }

    @Test
    fun missingTilesAreAppendedAndUnknownOnesDropped() {
        val decoded = decodeDashboardLayout("RECENT:1:1:5|BOGUS:1:0:3")
        assertEquals(DashboardTileId.RECENT, decoded.first().id)
        assertEquals(5, decoded.first().rows)
        assertEquals(DashboardTileId.entries.toSet(), decoded.map { it.id }.toSet())
    }

    @Test
    fun rowsAreClamped() {
        val decoded = decodeDashboardLayout("RECENT:1:1:99")
        assertEquals(MAX_RECENT_ROWS, decoded.first { it.id == DashboardTileId.RECENT }.rows)
    }
}

class ConnectionConfigTest {
    @Test
    fun baseUrlNormalizesHost() {
        assertEquals("https://hb.example.com/", ConnectionConfig(host = "https://hb.example.com/").baseUrl())
        assertEquals("http://10.0.0.5:7745/", ConnectionConfig(scheme = "http", host = "10.0.0.5", port = "7745").baseUrl())
        // A port typed into the host is replaced by the port field, not doubled.
        assertEquals("http://10.0.0.5:8080/", ConnectionConfig(scheme = "http", host = "10.0.0.5:7745", port = "8080").baseUrl())
    }

    @Test
    fun authHeaderHasOneBearerPrefix() {
        assertEquals("Bearer abc", ConnectionConfig(token = "abc").authHeader())
        assertEquals("Bearer abc", ConnectionConfig(token = "Bearer abc").authHeader())
        assertNull(ConnectionConfig(token = " ").authHeader())
    }
}
