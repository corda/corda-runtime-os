package net.corda.httprpc.durablestream.api

import net.corda.v5.base.annotations.DoNotImplement
import java.time.Duration

/**
 * Extension of [DurableCursor] which hints that returned stream will be finite, i.e. it will have an end.
 * Also, finite cursors indicate that a full result set is available on the server side and the client can consume
 * the whole result at their convenience.
 */
@DoNotImplement
interface FiniteDurableCursor<T> : DurableCursor<T> {
    /**
     * Convenience method over [DurableCursor.poll] which instructs not to spend any time waiting for result on the server side.
     */
    fun take(maxCount: Int): Cursor.PollResult<T> = poll(maxCount, Duration.ZERO)
}