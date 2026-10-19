package com.safemode.hbconnect.ui.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkHostTest {
    @Test
    fun privateAndLocalHosts() {
        listOf(
            "192.168.1.50", "10.0.0.5:7745", "172.20.1.1", "127.0.0.1", "100.101.102.103",
            "localhost", "homebox", "nas.local", "hb.home.arpa", "box.tail1234.ts.net",
            "[fd00::1]:7745", "::1",
        ).forEach { assertTrue(it, isLocalNetworkHost(it)) }
    }

    @Test
    fun publicHosts() {
        listOf("hb.example.com", "8.8.8.8", "172.32.0.1", "http://hb.example.com/path", "[2001:db8::1]")
            .forEach { assertFalse(it, isLocalNetworkHost(it)) }
    }
}
