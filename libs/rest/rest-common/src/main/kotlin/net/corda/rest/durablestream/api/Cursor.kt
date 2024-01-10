package net.corda.rest.durablestream.api

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.rest.durablestream.api.Cursor.PollResult
import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * A [Cursor] is a stream that emits elements of type [T], providing [poll] as the mechanism to retrieve elements.
 *
 * Expected usage of this interface consists of the following steps:
 *
 * - Call [poll] to retrieve a batch of elements stored in a [PollResult].
 * - Process these elements using either [PollResult.positionedValues] or [PollResult.values].
 * - Depending on whether there are more batches to retrieve, repeat the 2 previous steps. [PollResult.isLastResult] indicates whether the
 * returned [PollResult] contains the last batch of elements.
 *
 * Example usage:
 *
 * - Kotlin:
 *
 * ```kotlin
 * var result: PollResult<T> = cursor.poll(50, 5.minutes)
 * while (!result.isLastResult) {
 *     for (positionedValue: PositionedValue<T> in result.positionedValues) {
 *         log.info("Processing value: ${positionedValue.value} at position: ${positionedValue.position}")
 *     }
 *     result = cursor.poll(50, 5.minutes)
 * }
 * ```
 *
 * - Java:
 *
 * ```java
 * PollResult<T> result = cursor.poll(50, Duration.of(5, ChronoUnit.MINUTES));
 * while (!result.isLastResult()) {
 *     for (PositionedValue<T> positionedValue : result.getPositionedValues()) {
 *         log.info("Processing value: " + positionedValue.getValue() + " at position: " + positionedValue.getPosition());
 *     }
 *     result = cursor.poll(50, Duration.of(5, ChronoUnit.MINUTES));
 * }
 * ```
 *
 * @see DurableCursor
 */
@DoNotImplement
interface Cursor<T> {

    /**
     * Asynchronously tries to retrieve a batch of elements if they are available.
     *
     * @param maxCount The maximum number of elements to be returned.
     * @param awaitForResultTimeout  The desired maximum duration to wait for result to become available. The [CompletableFuture] will be
     * completed when [maxCount] number of elements is available or [awaitForResultTimeout] has elapsed, whichever happens first.
     *
     * @return A [CompletableFuture] over [Cursor.PollResult].
     */
    fun asyncPoll(maxCount: Int, awaitForResultTimeout: Duration): CompletableFuture<PollResult<T>> {
        return CompletableFuture.supplyAsync { poll(maxCount, awaitForResultTimeout) }
    }

    /**
     * Retrieve a batch of elements if they are available.
     *
     * @param maxCount The maximum number of elements to be returned.
     * @param awaitForResultTimeout The desired maximum duration to wait for result to become available.
     *
     * @return A [PollResult] containing the batch of requested elements.
     *
     * @throws IllegalArgumentException If the [awaitForResultTimeout] is negative.
     */
    @Suspendable
    fun poll(maxCount: Int, awaitForResultTimeout: Duration): PollResult<T>

    /**
     * A data container which represents a batch of elements along with their positions.
     */
    @CordaSerializable
    @DoNotImplement
    interface PollResult<T> {

        /**
         * Values with positions
         */
        val positionedValues: List<PositionedValue<T>>

        /**
         * Convenience property to retrieve just the raw elements.
         */
        val values: List<T> get() = positionedValues.map { it.value }

        /**
         * First position in the batch.
         *
         * @throws NoSuchElementException If there are no values.
         */
        val firstPosition: Long get() = positionedValues.first().position

        /**
         * Last position in the batch.
         *
         * @throws NoSuchElementException If there are no values.
         */
        val lastPosition: Long get() = positionedValues.last().position

        /**
         * A non-negative optional estimate for the remaining elements count.
         */
        val remainingElementsCountEstimate: Long?

        /**
         * Whether this result has no elements.
         *
         * @returns `true` if there a no elements, `false` otherwise.
         */
        val isEmpty: Boolean get() = positionedValues.isEmpty()

        /**
         * Whether this result represents the last batch of elements.
         *
         * @returns `true` if there are no more elements to [poll], `false` otherwise.
         */
        val isLastResult: Boolean

        /**
         * Position with value data container.
         */
        @CordaSerializable
        @DoNotImplement
        interface PositionedValue<T> {
            val value: T
            val position: Long
        }
    }
}