package net.corda.internal.serialization.amqp.api

import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.serialization.SerializedBytes

interface SerializationServiceInternal : SerializationService {

    /**
     * Serializes the input [ob].
     *
     * @param obj The object to serialize.
     * @param withCompression Whether compression will be applied.
     *
     * @return [SerializedBytes] containing the serialized representation of the input object.
     */
    fun <T : Any> serialize(obj: T, withCompression: Boolean): SerializedBytes<T>
}