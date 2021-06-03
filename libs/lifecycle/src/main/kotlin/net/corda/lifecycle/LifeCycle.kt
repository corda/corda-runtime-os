package net.corda.lifecycle

/**
 * This interface defines a component it can [start] and [stop] and be used as a try-with-resource as
 *
 * ```kotlin
 * object: LifeCycle { ... }.use { lifecycle -> ... }
 * ```
 *
 * When the component goes out of scope, [close] is automatically called, hence [stop].
 */
interface LifeCycle: AutoCloseable {

    /**
     * It is `true` the component is running.
     */
    val isRunning: Boolean

    /**
     * Override to define how the component starts.
     */
    fun start()

    /**
     * Override to define how the component stops: close and release resources in this method.
     */
    fun stop()

    //: AutoCloseable

    /**
     * Automatically called when this component is out of try-with-resource scope.
     *
     * See [AutoCloseable.close]
     */
    override fun close() {
        stop()
    }
}

