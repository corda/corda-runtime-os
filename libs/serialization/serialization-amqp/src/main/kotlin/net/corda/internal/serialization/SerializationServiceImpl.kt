package net.corda.internal.serialization

import net.corda.v5.application.services.serialization.SerializationService
import net.corda.v5.base.types.sequence
import net.corda.v5.serialization.SerializationContext
import net.corda.v5.serialization.SerializedBytes
import net.corda.v5.serialization.SingletonSerializeAsToken

internal class SerializationServiceImpl(
    private val serializationEnvironment: SerializationEnvironment,
    private val context: SerializationContext
) : SerializationService {

    override fun <T : Any> serialize(obj: T): SerializedBytes<T> {
        return obj.serialize(
            serializationEnvironment.serializationFactory,
            context
        )
    }

    override fun <T : Any> deserialize(serializedBytes: SerializedBytes<T>, clazz: Class<T>): T {
        return serializationEnvironment.serializationFactory.deserialize(
            serializedBytes,
            clazz,
            context
        )
    }

    override fun <T : Any> deserialize(bytes: ByteArray, clazz: Class<T>): T {
        return serializationEnvironment.serializationFactory.deserialize(
            bytes.sequence(),
            clazz,
            context
        )
    }
}

/**
 * P2P implementation of [SerializationService] and [P2pSerializationService].
 *
 * Extends [SingletonSerializeAsToken] so that it can be used within flows.
 */
internal class P2pSerializationServiceImpl(delegate: SerializationService) : SingletonSerializeAsToken,
    SerializationService by delegate, P2pSerializationService

/**
 * Storage implementation of [SerializationService] and [StorageSerializationService].
 */
internal class StorageSerializationServiceImpl(delegate: SerializationService) : SerializationService by delegate,
    StorageSerializationService