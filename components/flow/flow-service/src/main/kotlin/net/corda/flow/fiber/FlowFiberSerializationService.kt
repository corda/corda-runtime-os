package net.corda.flow.fiber

import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SerializedBytes

/**
 * [FlowFiberSerializationService] provides simplified access to the AMQP [SerializationService] assigned to the
 * currently executing flow's sandbox.
 */
interface FlowFiberSerializationService : SerializationService {

    /**
     * Serializes the input [obj] using the flow's [SerializationService].
     *
     * @param obj The object to serialize.
     *
     * @return [SerializedBytes] containing the serialized representation of the input object.
     */
    override fun <T : Any> serialize(obj: T): SerializedBytes<T>

    /**
     * Deserializes the input serialized bytes into an object of type [T] using the flow's [SerializationService].
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
    override fun <T : Any> deserialize(bytes: ByteArray, clazz: Class<T>): T

    /**
     * Deserializes the input serialized bytes into an object of type [T] using the flow's [SerializationService].
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
    override fun <T : Any> deserialize(serializedBytes: SerializedBytes<T>, clazz: Class<T>): T {
        TODO("Not yet implemented")
    }
}

class DeserializedWrongAMQPObjectException(
    val expectedType: Class<*>,
    val deserializedType: Class<*>,
    val deserializedObject: Any,
    message: String
) : CordaRuntimeException(message)