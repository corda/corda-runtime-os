package net.corda.lifecycle

import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.javaField

/**
 * Encapsulates a group of Dependent components that should be registered, started and stopped together.
 *
 * NOTE: This is only safe for static singleton components.  It is not safe or intended for resources
 * which may be stopped, closed and recreated during normal execution.
 *
 * @property map
 * @constructor Create empty Dependent components
 */
class DependentComponents private constructor(private val map: Map<LifecycleCoordinatorName, Lifecycle>) {

    companion object {
        /**
         * Create a [DependentComponents] object from the list of Properties passed in.
         * When registered, this will track the given components with the (default) null
         * instance ID.
         *
         * @param properties a list of the dependencies (as class properties) that will be tracked
         * @return a [DependentComponents] to manage the [properties].
         */
        fun of(vararg properties: KProperty0<Lifecycle>): DependentComponents {
            return DependentComponents(
                properties.associate { LifecycleCoordinatorName(it.javaField!!.type.name) to it.get() }
            )
        }

        /**
         * Return a new [DependentComponents] object with the given [property]
         * and [instanceId]
         *
         * @param property
         * @param instanceId
         * @return
         */
        fun with(property: KProperty0<Lifecycle>, instanceId: String?): DependentComponents {
            return with(emptyMap(), property, instanceId)
        }

        private fun with(
            map: Map<LifecycleCoordinatorName, Lifecycle>,
            property: KProperty0<Lifecycle>,
            instanceId: String?
        ): DependentComponents {
            val name = LifecycleCoordinatorName(property.javaField!!.type.name, instanceId)
            return DependentComponents(map.plus(Pair(name, property.get())))
        }
    }

    /**
     * Return a new [DependentComponents] object with the given [property]
     * and [instanceId]
     *
     * @param property
     * @param instanceId
     * @return
     */
    fun with(property: KProperty0<Lifecycle>, instanceId: String?): DependentComponents {
        return DependentComponents.with(map, property, instanceId)
    }

    private var registration: RegistrationHandle? = null

    val coordinatorNames: Set<LifecycleCoordinatorName> = map.keys

    fun stopAll() {
        map.values.forEach { it.stop() }
        registration?.close()
        registration = null
    }

    fun registerAndStartAll(coordinator: LifecycleCoordinator) {
        registration?.close()
        registration = coordinator.followStatusChangesByName(map.keys)
        map.values.forEach { it.start() }
    }
}
