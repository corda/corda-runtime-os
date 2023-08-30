package net.corda.libs.statemanager.api

import java.time.Instant

/**
 * Mutable map that only allows primitive types to be used as values.
 */
class PrimitiveTypeMap<K, V : Any>(
    private val map: MutableMap<K, V> = mutableMapOf()
) : MutableMap<K, V> by map {

    override fun put(key: K, value: V): V? {
        if (!(value::class.java.isPrimitive) && (value !is String)) {
            throw IllegalArgumentException("Only primitive types are allowed: ${value::class.simpleName}")
        }

        return map.put(key, value)
    }
}

/**
 * A state managed via the state manager.
 */
data class State<S>(
    /**
     * The typed state itself.
     */
    val state: S,

    /**
     * Identifier for the state.
     */
    val key: String,

    /**
     * Version of the state.
     */
    val version: Int,

    /**
     * Time when the state was last modified.
     */
    val modifiedTime: Instant,

    /**
     * Arbitrary Map of primitive types that can be used to store and query extra data associated with the state
     * that must generally not be part of the state itself.
     */
    val metadata: PrimitiveTypeMap<String, Any> = PrimitiveTypeMap()
)
