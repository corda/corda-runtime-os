@file:JvmName("SerializationAPI")
package net.corda.serialization

import net.corda.v5.base.types.ByteSequence

typealias SerializationMagic = ByteSequence

interface SerializationEncoding

interface EncodingWhitelist {
    fun acceptEncoding(encoding: SerializationEncoding): Boolean
}

data class ObjectWithCompatibleContext<out T : Any>(val obj: T, val context: SerializationContext)

/**
 * Set of well known properties that may be set on a serialization context. This doesn't preclude
 * others being set that aren't keyed on this enumeration, but for general use properties adding a
 * well known key here is preferred.
 */
enum class ContextPropertyKeys {
    SERIALIZERS
}

