@file:JvmName("SerializationUtils")
package net.corda.v5.application.serialization

import net.corda.v5.serialization.SerializedBytes

/**
 * Deserializes the input serialized bytes into an object of type [T].
 *
 * @param serializedBytes The [SerializedBytes] to deserialize.
 * @param T The type to deserialize to.
 *
 * @return A new instance of type [T] created from the input [serializedBytes].
 */
inline fun <reified T : Any> SerializationService.deserialize(serializedBytes: SerializedBytes<T>): T {
    return this.deserialize(serializedBytes, T::class.java)
}

/**
 * Deserializes the input serialized bytes into an object of type [T].
 *
 * @param bytes The [ByteArray] to deserialize.
 * @param T The type to deserialize to.
 *
 * @return A new instance of type [T] created from the input [bytes].
 */
inline fun <reified T : Any> SerializationService.deserialize(bytes: ByteArray): T {
    return this.deserialize(bytes, T::class.java)
}
