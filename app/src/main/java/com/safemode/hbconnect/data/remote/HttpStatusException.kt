package com.safemode.hbconnect.data.remote

import java.io.IOException

/** A request the server answered with a non-success HTTP [code]; [message] is readable for the UI. */
class HttpStatusException(val code: Int, message: String) : IllegalStateException(message)

/**
 * True when this failure means the server couldn't be reached: no connection, a timeout, or a
 * gateway error from a reverse proxy in front of a stopped server. The last-synced copy is then the
 * best answer for a read. An answer from Homebox itself (401, 403, 404, a validation error) is not
 * included, so an expired session or a deleted item shows as such instead of as stale data.
 */
fun Throwable.isUnreachable(): Boolean =
    this is IOException || (this is HttpStatusException && code in GATEWAY_ERRORS)

private val GATEWAY_ERRORS = setOf(502, 503, 504)
