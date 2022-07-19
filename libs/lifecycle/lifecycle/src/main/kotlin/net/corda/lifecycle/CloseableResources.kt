package net.corda.lifecycle

import kotlin.reflect.KProperty0

class CloseableResources private constructor(private val resources: Set<KProperty0<AutoCloseable?>>) {
    companion object {
        fun of(
            vararg properties: KProperty0<AutoCloseable?>,
        ): CloseableResources {
            return CloseableResources(
                properties.toSet(),
            )
        }
    }

    fun closeResources() {
        resources.forEach { it.get()?.close() }
    }
}
