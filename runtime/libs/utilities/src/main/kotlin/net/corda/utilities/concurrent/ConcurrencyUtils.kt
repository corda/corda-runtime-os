@file:JvmName("ConcurrencyUtils")

package net.corda.utilities.concurrent

import java.time.Duration
import java.util.Objects
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.allOf
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.stream.Collectors

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

/**
 * If all of the given futures succeed, the returned future's outcome is a list of all their values.
 * The values are in the same order as the futures in the collection, not the order of completion.
 * If at least one given future fails, the returned future's outcome is the first throwable that was thrown.
 * If no futures were given, the returned future has an immediate outcome of empty list.
 * Otherwise the returned future does not have an outcome until all given futures have an outcome.
 * Unlike Guava's Futures.allAsList, this method never hides failures/hangs subsequent to the first failure.
 */
fun <V> Collection<CompletableFuture<out V>>.transpose(): CompletableFuture<List<V>> {
    if (isEmpty()) return completedFuture(emptyList())
    return allOf(*toTypedArray())
        .thenApply {
            stream()
                .map { it.join() }
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
        }
}

/** Run the given block (in the foreground) and set this future to its outcome. */
fun <V> CompletableFuture<V>.capture(block: () -> V): Boolean {
    return complete(
        try {
            block()
        } catch (e: Exception) {
            return completeExceptionally(e)
        } catch (t: Throwable) {
            completeExceptionally(t)
            throw t
        }
    )
}
