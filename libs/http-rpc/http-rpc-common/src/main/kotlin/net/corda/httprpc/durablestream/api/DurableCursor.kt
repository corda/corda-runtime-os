package net.corda.httprpc.durablestream.api

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.httprpc.durablestream.api.Cursor.PollResult
import java.util.concurrent.CompletableFuture

/**
 * A [DurableCursor] is a stream that emits elements of type [T] and provides management functions to maintain durability across application
 * restarts.
 *
 * A [DurableCursor] can represent both a finite and infinite stream of elements. APIs returning a cursor will determine which type the
 * cursor belongs to.
 *
 * Finite streams should consist of the following steps:
 *
 * - Call [poll] to retrieve a batch of elements stored in a [PollResult].
 * - Process these elements using either [PollResult.positionedValues] or [PollResult.values].
 * - Call [commit] to update the position that the cursor has successfully processed elements up to.
 * - Depending on whether there are more batches to retrieve, repeat the 3 previous steps. [PollResult.isLastResult] indicates whether the
 * returned [PollResult] contains the last batch of elements.
 *
 * Example usage of an finite stream:
 *
 * - Kotlin:
 *
 * ```kotlin
 * var result: PollResult<T> = cursor.poll(50, 5.minutes)
 * while (!result.isLastResult) {
 *     for (positionedValue: PositionedValue<T> in result.positionedValues) {
 *         log.info("Processing value: ${positionedValue.value} at position: ${positionedValue.position}")
 *     }
 *     cursor.commit(result)
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
 *     cursor.commit(result)
 *     result = cursor.poll(50, Duration.of(5, ChronoUnit.MINUTES));
 * }
 * ```
 *
 * Infinite streams should consist of the following steps:
 *
 * - Call [poll] to retrieve a batch of elements stored in a [PollResult].
 * - Process these elements using either [PollResult.positionedValues] or [PollResult.values].
 * - Call [commit] to update the position that the cursor has successfully processed elements up to.
 * - Repeat the 3 previous steps. There is no conditional check based on the [PollResult] here because the stream is expected to continue
 * infinitely.
 *
 * Example usage of an infinite stream:
 *
 * - Kotlin:
 *
 * ```kotlin
 * while (!Thread.currentThread().isInterrupted) {
 *     val result = cursor.poll(50, 5.minutes);
 *     for (positionedValue: PositionedValue<T> in result.positionedValues) {
 *         log.info("Processing value: ${positionedValue.value} at position: ${positionedValue.position}")
 *     }
 *     cursor.commit(result.lastPosition)
 * }
 * ```
 *
 * - Java:
 *
 * ```java
 * while (!Thread.currentThread().isInterrupted()) {
 *     PollResult<T> result = cursor.poll(50, Duration.of(5, ChronoUnit.MINUTES));
 *     for (PositionedValue<T> positionedValue : result.getPositionedValues()) {
 *         log.info("Processing value: " + positionedValue.getValue() + " at position: " + positionedValue.getPosition());
 *     }
 *     cursor.commit(result);
 * }
 * ```
 *
 * @see Cursor
 */
@DoNotImplement
interface DurableCursor<T> : Cursor<T> {

    /**
     * Gets the current position of the cursor
     *
     * @throws CursorException If the cursor fails to return its current position.
     */
    @Suppress("TooGenericExceptionCaught")
    val currentPosition: Long
        get() {
            return try {
                positionManager.get()
            } catch (e: Exception) {
                throw CursorException("Failed to retrieve the cursor position", e)
            }
        }

    /**
     * Adjusts the cursor to a specific position either back or forward. [position] may not be less than `-1`.
     *
     * @throws CursorException If the cursor fails to adjust its position.
     */
    @Suppress("TooGenericExceptionCaught")
    fun seek(position: Long) = try {
        positionManager.accept(position)
    } catch (e: Exception) {
        throw CursorException("Failed to adjust the cursor position to $position", e)
    }

    /**
     * Resets the cursor's position to the very beginning of the stream.
     *
     * @throws CursorException If the cursor fails to adjust its position.
     */
    fun reset() = seek(PositionManager.MIN_POSITION)

    /**
     * Marks all the elements up to a [position] (inclusive) as consumed. Such that future calls to [poll] will return elements further
     * down the stream.
     *
     * @return A [CompletableFuture] over [Unit] since operation may take some time.
     */
    fun asyncCommit(position: Long): CompletableFuture<Unit> {
        return CompletableFuture.supplyAsync { commit(position) }
    }

    /**
     * Marks all the elements up to a [position] (inclusive) as consumed. Such that future calls to [poll] will return elements further
     * down the stream.
     *
     * @param position The position to commit.
     *
     * @throws CursorException If the new position fails to commit.
     */
    @Suppress("TooGenericExceptionCaught")
    fun commit(position: Long) {
        try {
            positionManager.accept(position)
        } catch (e: Exception) {
            throw CursorException("Failed to commit the cursor position as $position", e)
        }
    }

    /**
     * Convenience method that commits [Cursor.PollResult.lastPosition] for non-empty results.
     *
     * @param result The [Cursor.PollResult] who's [Cursor.PollResult.lastPosition] will be committed.
     *
     * @throws CursorException If the new position fails to commit.
     */
    fun commit(result: PollResult<T>) {
        if (!result.isEmpty) {
            commit(result.lastPosition)
        }
    }

    /**
     * Gets the [PositionManager] of the cursor.
     */
    val positionManager: PositionManager
}