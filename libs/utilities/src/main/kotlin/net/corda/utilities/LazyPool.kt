package net.corda.utilities

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

/**
 * A lazy pool of resources [A].
 *
 * @param clear If specified this function will be run on each borrowed instance before handing it over.
 * @param shouldReturnToPool If specified this function will be run on each release to determine whether the instance
 *     should be returned to the pool for reuse. This may be useful for pooled resources that dynamically grow during
 *     usage, and we may not want to retain them forever.
 * @param bound If specified the pool will be bounded. Once all instances are borrowed subsequent borrows will block until an
 *     instance is released.
 * @param newInstance The function to call to lazily newInstance a pooled resource.
 */
class LazyPool<A>(
    private val clear: ((A) -> Unit)? = null,
    private val shouldReturnToPool: ((A) -> Boolean)? = null,
    private val bound: Int? = null,
    private val newInstance: () -> A
) {
    private val poolQueue = ConcurrentLinkedQueue<A>()
    private val poolSemaphore = Semaphore(bound ?: Int.MAX_VALUE)

    private enum class State {
        STARTED,
        FINISHED
    }

    private val lifecycle = PoolState(State.STARTED)

    private fun clearIfNeeded(instance: A): A {
        clear?.invoke(instance)
        return instance
    }

    fun borrow(): A {
        lifecycle.requireState(State.STARTED)
        poolSemaphore.acquire()
        val pooled = poolQueue.poll()
        return if (pooled == null) {
            newInstance()
        } else {
            clearIfNeeded(pooled)
        }
    }

    fun release(instance: A) {
        lifecycle.requireState(State.STARTED)
        if (shouldReturnToPool == null || shouldReturnToPool.invoke(instance)) {
            poolQueue.add(instance)
        }
        poolSemaphore.release()
    }

    /**
     * Closes the pool. Note that all borrowed instances must have been released before calling this function, otherwise
     * the returned iterable will be inaccurate.
     */
    fun close(): Iterable<A> {
        lifecycle.justTransition(State.FINISHED)
        // Does not use kotlin toList() as it currently is not safe to use on concurrent data structures.
        val elements = ArrayList(poolQueue)
        poolQueue.clear()
        return elements
    }

    inline fun <R> run(withInstance: (A) -> R): R {
        val instance = borrow()
        try {
            return withInstance(instance)
        } finally {
            release(instance)
        }
    }

    private class PoolState<S : Enum<S>>(initial: S) {
        private val lock = ReentrantReadWriteLock()
        private var state = initial

        fun requireState(requiredState: S) = lock.readLock().withLock {
            require(state == requiredState) { "Required state to be $requiredState, was $state" }
        }

        /** Transition the state from [from] to [to]. */
        fun transition(from: S, to: S) {
            lock.writeLock().withLock {
                require(state == from) { "Required state to be $from to transition to $to, was $state" }
                state = to
            }
        }

        /** Transition the state to [to] without performing a current state check. */
        fun justTransition(to: S) {
            lock.writeLock().withLock {
                state = to
            }
        }
    }
}