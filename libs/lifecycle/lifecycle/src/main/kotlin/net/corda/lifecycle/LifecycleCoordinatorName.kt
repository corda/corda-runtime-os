package net.corda.lifecycle

/**
 * Identifier for a lifecycle coordinator.
 *
 * This identifier is used to show which component a particular coordinator is for. This is used when looking up other
 * components to
 * register against when implementing domino logic. It is also the key by which coordinator statuses are
 * recorded in the registry.
 *
 * Note that creating two coordinators with equal names is considered an error. Where multiple components of the same
 * name must be created in the same process, the instance ID should be used to distinguish between them. True singleton
 * components can use the componentName as the sole identifier.
 *
 * @param componentName The name of the component
 * @param instanceId If this component can be instantiated more than once, this is used to disambiguate the different
 *                   instances. Can be left `null` for singleton components.
 */
data class LifecycleCoordinatorName(
    val componentName: String,
    val instanceId: String? = null
) {
    companion object {
        /**
         * Create a name using the class name of the component as the component name.
         *
         * @param instanceId If this component can be instantiated more than once, this is used to disambiguate the
         *                   different instances. Can be left `null` for singleton components.
         */
        inline fun <reified T> forComponent(instanceId: String? = null): LifecycleCoordinatorName {
            return LifecycleCoordinatorName(T::class.java.name, instanceId)
        }
    }

    override fun toString(): String {
        return if (instanceId != null) {
            "$componentName-$instanceId"
        } else {
            componentName
        }
    }
}
