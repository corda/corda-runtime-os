package net.corda.lifecycle

import kotlin.reflect.KProperty0

/**
 * Encapsulates a group of owned resources which should be closed and recreated upon a [CloseableResourceEvent].
 *
 * Helps to ensure that such resources are correctly closed when such events, e.g. a config change, occur.
 *
 * @property resources the set of tracked closeable resources (must be [AutoCloseable])
 */
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

    /**
     * Closes all resources tracked by this [CloseableResources].
     */
    fun closeResources() {
        resources.forEach { it.get()?.close() }
    }
}
