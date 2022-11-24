package net.corda.lifecycle

/**
 * This interface defines a resource owned and controlled by a component.
 * It can be used as a try-with-resource as
 *
 * ```kotlin
 * object: Resource { ... }.use { lifecycle -> ... }
 * ```
 *
 * When the resource goes out of scope, [close] is automatically called
 */
interface Resource : AutoCloseable {
    /**
     * Automatically called when this resource is out of try-with-resource scope.
     *
     * Further, it is not expected that a closed object should be restarted.
     *
     * See [AutoCloseable.close]
     */
    override fun close()
}
