package net.corda.processors.db.internal

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorName
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.javaField

class DependentComponents(private val map: Map<LifecycleCoordinatorName, Lifecycle>) {

    companion object {
        fun of(vararg properties: KProperty0<Lifecycle>): DependentComponents {
            val interimMap: Map<Class<*>, Lifecycle> = properties.associate {
                it.javaField!!.type to it.get()
            }
            val map = interimMap.mapKeys { LifecycleCoordinatorName(it.key.name, null) }
            return DependentComponents(map)
        }
    }

    val coordinatorNames: Set<LifecycleCoordinatorName> = map.keys

    fun startAll() {
        map.values.forEach { it.start() }
    }

    fun stopAll() {
        map.values.forEach { it.stop() }
    }
}