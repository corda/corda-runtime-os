package net.corda.v5.application.serialization

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.serialization.SerializedBytes

/**
 * Handles serialization and deserialization of objects.
 */
@DoNotImplement
interface SerializationService {

    /**
     * Serializes the input [obj].
     *
     * @param obj The object to serialize.
     *
     * @return [SerializedBytes] containing the serialized representation of the input object.
     */
    fun <T : Any> serialize(obj: T): SerializedBytes<T>

    /**
     * Deserializes the input serialized bytes into an object of type [T].
     *
     * @param serializedBytes The [SerializedBytes] to deserialize.
     * @param clazz [Class] containing the type [T] to deserialize to.
     * @param T The type to deserialize to.
     *
     * @return A new instance of type [T] created from the input [serializedBytes].
     */
    fun <T : Any> deserialize(serializedBytes: SerializedBytes<T>, clazz: Class<T>): T

    /**
     * Deserializes the input serialized bytes into an object of type [T].
     *
     * @param bytes The [ByteArray] to deserialize.
     * @param clazz [Class] containing the type [T] to deserialize to.
     * @param <T> The type to deserialize to.
     *
     * @return A new instance of type [T] created from the input [bytes].
     */
    fun <T : Any> deserialize(bytes: ByteArray, clazz: Class<T>): T
}

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