@file:JvmName("ConcurrencyUtils")

package net.corda.base.concurrent

import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Waits if necessary for the computation to complete, and then retrieves its result.
 *
 * This function is the same as [Future.get] except that it throws the underlying exception by unwrapping the [ExecutionException].
 *
 * @param timeout The maximum duration to wait until the future completes.
 *
 * @return The computed result.
 *
 * @throws CancellationException If the computation was cancelled.
 * @throws InterruptedException  If the current thread was interrupted while waiting.
 * @throws TimeoutException      If the wait timed out.
 * @see getOrThrow()
 * @see Future.get(long timeout, TimeUnit unit)
 */
@Throws(InterruptedException::class, TimeoutException::class)
fun <V> Future<V>.getOrThrow(timeout: Duration?): V = try {
    if (timeout == null) get() else get(timeout.toNanos(), TimeUnit.NANOSECONDS)
} catch (e: ExecutionException) {
    throw e.cause ?: IllegalStateException("ExecutionException without cause", e)
}

/**
 * Waits if necessary for the computation to complete, and then retrieves its result.
 *
 * This function is the same as [Future.get] except that it throws the underlying exception by unwrapping the [ExecutionException].
 *
 * @return The computed result.
 *
 * @throws CancellationException If the computation was cancelled.
 * @throws InterruptedException  If the current thread was interrupted while waiting.
 * @throws TimeoutException      If the wait timed out.
 * @see getOrThrow()
 * @see Future.get(long timeout, TimeUnit unit)
 */
@Throws(InterruptedException::class, TimeoutException::class)
fun <V> Future<V>.getOrThrow(): V = getOrThrow(null)