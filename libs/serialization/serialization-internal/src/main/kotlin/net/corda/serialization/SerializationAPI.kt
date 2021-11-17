@file:JvmName("SerializationAPI")
package net.corda.serialization

import net.corda.v5.base.types.ByteSequence

typealias SerializationMagic = ByteSequence

interface SerializationEncoding

interface ClassWhitelist {
    fun hasListed(type: Class<*>): Boolean
}

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

/**
 * Helper method to return a new context based on this context with the
 * given list of classes specifically whitelisted.
 */
fun SerializationContext.withWhitelist(classes: List<Class<*>>): SerializationContext {
    var currentContext = this
    classes.forEach {
            clazz -> currentContext = currentContext.withWhitelisted(clazz)
    }

    return currentContext
}
