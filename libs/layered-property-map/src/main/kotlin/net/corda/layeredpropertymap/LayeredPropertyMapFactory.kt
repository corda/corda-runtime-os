package net.corda.layeredpropertymap

import net.corda.v5.base.types.LayeredPropertyMap

/**
 * Factory for creating instances of [LayeredPropertyMap].
 */
interface LayeredPropertyMapFactory {
    /**
     * Creates an instance implementing [LayeredPropertyMap] for the given map os string to string.
     */
    fun createMap(properties: Map<String, String?>): LayeredPropertyMap
}

/**
 * Creates an instance of a concrete class [T] derived from [LayeredPropertyMap] for the given map os string to string.
 * The class must implement a constructor with a single parameter of [LayeredPropertyMap].
 */
inline fun <reified T: LayeredPropertyMap> LayeredPropertyMapFactory.create(properties: Map<String, String?>): T {
    val constructor = T::class.java.getConstructor(LayeredPropertyMap::class.java)
    val map = createMap(properties)
    return constructor.newInstance(map)
}