package net.corda.serialization

import java.io.NotSerializableException

/**
 *The serializer interface for serde methods on a when checkpointing (i.e. Quasar suspend)
 */
interface CheckpointSerializer : NonSerializable {

    /**
     * Deserialize the checkpoint from [bytes] into a type of [clazz]
     *
     * @param bytes the serialized byte stream (from the [serialize] call
     * @param clazz the expected type of the deserialized [bytes]
     * @return a deserialized instance of [clazz]
     */
    @Throws(NotSerializableException::class)
    fun <T : Any> deserialize(bytes: ByteArray, clazz: Class<T>): T

    /**
     * Serialize [obj] into a byte stream
     *
     * @param obj the object to be serialized
     * @return the byte stream representing [obj]
     */
    @Throws(NotSerializableException::class)
    fun <T : Any> serialize(obj: T): ByteArray
}
