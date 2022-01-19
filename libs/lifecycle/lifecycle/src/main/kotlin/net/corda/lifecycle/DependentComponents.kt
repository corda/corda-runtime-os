package net.corda.lifecycle

import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.javaField

/**
 * Encapsulates a group of Dependent components that should be registered, started and stopped together.
 *
 * @property map
 * @constructor Create empty Dependent components
 */
class DependentComponents(private val map: Map<LifecycleCoordinatorName, Lifecycle>) {

    companion object {
        /**
         * Create a [DependentComponents] object from the list of Properties passed in.
         * When registered, this will track the given components with the (default) null
         * instance ID.
         *
         * @param properties
         * @return
         */
        fun of(vararg properties: KProperty0<Lifecycle>): DependentComponents {
            return properties.fold(DependentComponents(emptyMap())) { dc, p ->
                dc.with(p, null)
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
            return with(emptyMap(), property, instanceId)
        }

        private fun with(
            map: Map<LifecycleCoordinatorName, Lifecycle>,
            property: KProperty0<Lifecycle>,
            instanceId: String?): DependentComponents {
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