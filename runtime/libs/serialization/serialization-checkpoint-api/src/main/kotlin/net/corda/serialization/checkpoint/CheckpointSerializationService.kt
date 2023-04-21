package net.corda.serialization.checkpoint

/**
 * Interface of the service used to serialize and deserialize checkpoints
 */
interface CheckpointSerializationService {

    /**
     * Serializes the input [obj].
     *
     * @param obj The object to serialize.
     *
     * @return [ByteArray] containing the serialized representation of the input object.
     */
    fun <T : Any> serialize(obj: T): ByteArray

    /**
     * Deserializes the input byte array into an object of type [T].
     *
     * @param bytes The [ByteArray] to deserialize.
     * @param clazz [Class] containing the type [T] to deserialize to.
     * @param <T> The type to deserialize to.
     *
     * @return A new instance of type [T] created from the input [bytes].
     */
    fun <T : Any> deserialize(bytes: ByteArray, clazz: Class<T>): T
}


