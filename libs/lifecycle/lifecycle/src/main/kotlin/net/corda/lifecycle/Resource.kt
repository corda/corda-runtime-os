package net.corda.lifecycle

/**
 * This interface defines a resource owned and controlled by a component.
 * It can [start] and [close] and be used as a try-with-resource as
 *
 * ```kotlin
 * object: Lifecycle { ... }.use { lifecycle -> ... }
 * ```
 *
 * When the resource goes out of scope, [close] is automatically called
 */
interface Resource : AutoCloseable {

    /**
     * Override to define how the resource starts.
     *
     * It should be safe to call start multiple times without side effects.
     */
//    fun start() {}

    /**
     * Automatically called when this resource is out of try-with-resource scope.
     *
     * Further, it is not expected that a closed object should be restarted.
     *
     * See [AutoCloseable.close]
     */
    override fun close()
}
