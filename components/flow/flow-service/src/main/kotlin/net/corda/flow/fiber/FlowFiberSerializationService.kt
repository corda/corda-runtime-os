package net.corda.flow.fiber

import net.corda.v5.serialization.SerializedBytes

interface FlowFiberSerializationService {

    /**
     * Deserialize a byte array using the flow fibers AMQP SerializationService
     * @param bytes ByteArray to deserialize
     * @param expectedType type of the object to deserialize
     * @return Deserialized object
     */
    fun <R : Any> deserializePayload(bytes: ByteArray, expectedType: Class<R>): R

    /**
     * Serialize an object using the flow fibers AMQP serialization service.
     * @param obj object to serialize
     * @return serialized bytes
     */
    fun <T : Any> serialize(obj: T): SerializedBytes<T>
}