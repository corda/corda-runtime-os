package net.corda.lifecycle

/**
 * This interface defines a component it can [start] and [stop] and be used as a try-with-resource as
 *
 * ```kotlin
 * object: Lifecycle { ... }.use { lifecycle -> ... }
 * ```
 *
 * When the component goes out of scope, [close] is automatically called, hence [stop].
 */
interface Lifecycle : AutoCloseable {

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

    //: AutoCloseable

    /**
     * Automatically called when this component is out of try-with-resource scope.
     *
     * Further, it is not expected that a closed object should be restarted.
     *
     * See [AutoCloseable.close]
     */
    override fun close() = stop()
}
