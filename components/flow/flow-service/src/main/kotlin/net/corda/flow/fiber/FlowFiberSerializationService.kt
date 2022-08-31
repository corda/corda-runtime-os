package net.corda.flow.fiber

import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SerializedBytes

/**
 * [FlowFiberSerializationService]
 */
interface FlowFiberSerializationService {

    /**
     * Deserialize a [ByteArray] using the flow fiber's AMQP [SerializationService].
     *
     * The deserialized object is validated to ensure that its type matches [expectedType].
     *
     * @param bytes The [ByteArray] to deserialize.
     * @param expectedType The type of the object to deserialize.
     *
     * @return The deserialized object.
     *
     * @throws DeserializedWrongAMQPObjectException If [bytes] deserializes into an object that is not an
     * [expectedType].
     */
    fun <R : Any> deserialize(bytes: ByteArray, expectedType: Class<R>): R

    /**
     * Serialize an object using the flow fiber's AMQP [SerializationService].
     *
     * @param obj The object to serialize.
     *
     * @return The [SerializedBytes] representation of [obj].
     */
    fun <T : Any> serialize(obj: T): SerializedBytes<T>
}

class DeserializedWrongAMQPObjectException(
    val expectedType: Class<*>,
    val deserializedType: Class<*>,
    val deserializedObject: Any,
    message: String
) : CordaRuntimeException(message)