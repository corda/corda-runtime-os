package net.corda.layeredpropertymap

import net.corda.v5.base.types.LayeredPropertyMap

/**
 * Factory for creating instances of [LayeredPropertyMap].
 */
interface LayeredPropertyMapFactory {
    /**
     * Creates an instance implementing [LayeredPropertyMap] for the given map os string to string.
     */
    fun create(properties: Map<String, String?>): LayeredPropertyMap
}