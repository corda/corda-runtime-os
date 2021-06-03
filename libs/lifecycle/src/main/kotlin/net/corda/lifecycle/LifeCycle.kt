package net.corda.lifecycle

/**
 * This interface define something it can [start] and [stop] and used as a try-with resource as
 *
 * ```kotlin
 * object: LifeCycle()
 * ```
 */
interface LifeCycle: AutoCloseable {

    val isRunning: Boolean

    fun start()

    fun stop()

    //: AutoCloseable

    override fun close() {
        stop()
    }
}

