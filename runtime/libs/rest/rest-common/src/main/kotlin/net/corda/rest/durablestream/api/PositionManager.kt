package net.corda.rest.durablestream.api

import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Responsible for storage and retrieval of the current position used by [DurableCursor]
 * By current position we mean position that has been already consumed and entities associated with this position
 * processed.
 *
 * API users can create their own implementations of this interface which can be assigned to [DurableCursor].
 *
 * In relation to multithreaded aspect of this interface:
 * Since it is really the caller of the [DurableCursor.poll] and [DurableCursor.commit] who controls the thread which
 * will be used for [PositionManager] invocations, there are no special provisions made to ensure thread safety of the
 * invocation to this interface.
 * The key principle here is that: Whichever position committed by calling [PositionManager.accept] should be available
 * when [PositionManager.get] is called.
 */
interface PositionManager : Supplier<Long>, Consumer<Long> {
    companion object {
        /**
         * Constant indicating position before the very first element of the stream.
         * I.e. this is the minimal possible value of the stream position.
         */
        const val MIN_POSITION = -1L
    }
}