package net.corda.lifecycle

/**
 * This interface defines a component it can [start] and [stop].
 */
interface Lifecycle {

    /**
     * It is `true` the component is running.
     */
    val isRunning: Boolean

    /**
     * Override to define how the component starts.
     *
     * It should be safe to call start multiple times without side effects.
     */
    fun start()

    /**
     * Override to define how the component stops: close and release resources in this method.
     *
     * It should be safe to call stop multiple times without side effects.
     */
    fun stop()
}
