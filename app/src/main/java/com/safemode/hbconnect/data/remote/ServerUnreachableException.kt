package com.safemode.hbconnect.data.remote

import java.io.IOException

/**
 * The server couldn't be reached and nothing usable was cached, so the request never left the
 * device. Raised in place of the synthetic 504 OkHttp returns for a cache-only lookup that misses
 * (see [OfflineInterceptor]), so callers see a readable reason instead of "Server error 504".
 */
class ServerUnreachableException : IOException("Server unreachable. Check your connection and try again.")
