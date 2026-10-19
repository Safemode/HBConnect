package com.safemode.hbconnect.data.queue

import com.safemode.hbconnect.data.remote.ServerUnreachableException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * True when [error] means the request certainly never reached the server: it couldn't connect at
 * all. Such a write is safe to queue for later. A failure after connecting (e.g. a read timeout) is
 * not included, since the server may already have applied the write and queueing it could repeat it.
 */
fun isUndelivered(error: Throwable?): Boolean = when (error) {
    is ServerUnreachableException, is ConnectException, is UnknownHostException, is NoRouteToHostException -> true
    // OkHttp reports a connect timeout as a SocketTimeoutException mentioning the connect phase.
    is SocketTimeoutException -> error.message?.contains("connect", ignoreCase = true) == true
    else -> false
}
