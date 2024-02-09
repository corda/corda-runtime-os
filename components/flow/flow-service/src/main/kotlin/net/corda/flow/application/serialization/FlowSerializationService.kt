package net.corda.flow.application.serialization

import net.corda.internal.serialization.amqp.api.SerializationServiceInternal
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SerializedBytes

/**
 * [FlowSerializationService] provides simplified access to the AMQP [SerializationService] assigned to the
 * currently executing flow's sandbox.
 */
interface FlowSerializationService : SerializationServiceInternal {

    /**
     * Deserializes the input serialized bytes into an object of type [T].
     *
     * The deserialized object is validated to ensure that its type matches [clazz].
     *
     * @param bytes The [ByteArray] to deserialize.
     * @param clazz [Class] containing the type [T] to deserialize to.
     * @param T The type to deserialize to.
     *
     * @return A new instance of type [T] created from the input [bytes].
     *
     * @throws DeserializedWrongAMQPObjectException If [bytes] deserializes into an object that is not a [clazz].
     */
    fun <T : Any> deserializeAndCheckType(bytes: ByteArray, clazz: Class<T>): T

    /**
     * Deserializes the input serialized bytes into an object of type [T].
     *
     * @param serializedBytes The [SerializedBytes] to deserialize.
     * @param clazz [Class] containing the type [T] to deserialize to.
     * @param T The type to deserialize to.
     *
     * @return A new instance of type [T] created from the input [serializedBytes].
     *
     * @throws DeserializedWrongAMQPObjectException If [serializedBytes] deserializes into an object that is not a
     * [clazz].
     */
    fun <T : Any> deserializeAndCheckType(serializedBytes: SerializedBytes<T>, clazz: Class<T>): T
}

class DeserializedWrongAMQPObjectException(
    val expectedType: Class<*>,
    val deserializedType: Class<*>,
    val deserializedObject: Any,
    message: String
) : CordaRuntimeException(message)