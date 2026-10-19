package com.safemode.hbconnect.data

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but lets coroutine cancellation propagate instead of turning it into a
 * failed [Result]. Plain runCatching inside a suspend function swallows the CancellationException,
 * so a cancelled load (e.g. the screen closed, or a newer reload replaced it) would carry on into
 * its failure path - falling back to the cache or setting an error state - instead of stopping.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
