package com.safemode.hbconnect.data.queue

import com.safemode.hbconnect.data.remote.ServerUnreachableException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class UndeliveredTest {
    @Test
    fun connectFailuresAreUndelivered() {
        assertTrue(isUndelivered(ConnectException("Failed to connect to /10.0.0.5:7745")))
        assertTrue(isUndelivered(UnknownHostException("hb.example.com")))
        assertTrue(isUndelivered(SocketTimeoutException("Connect timed out")))
        assertTrue(isUndelivered(ServerUnreachableException()))
    }

    @Test
    fun failuresAfterConnectingAreNot() {
        // The server may already have applied the write, so it must not be queued and repeated.
        assertFalse(isUndelivered(SocketTimeoutException("timeout")))
        assertFalse(isUndelivered(IOException("unexpected end of stream")))
        assertFalse(isUndelivered(IllegalStateException("Server error 500")))
        assertFalse(isUndelivered(null))
    }
}
