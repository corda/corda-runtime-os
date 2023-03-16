package net.corda.internal.serialization

import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.serialization.SerializationContext
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.types.ByteArrays.sequence
import net.corda.v5.serialization.SerializedBytes
import net.corda.v5.serialization.SingletonSerializeAsToken
import java.security.AccessController
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction

class SerializationServiceImpl(
    private val serializationOutput: SerializationOutput,
    private val deserializationInput: DeserializationInput,
    private val context: SerializationContext
) : SerializationService {

    override fun <T : Any> serialize(obj: T): SerializedBytes<T> {
        return try {
            AccessController.doPrivileged(PrivilegedExceptionAction {
                serializationOutput.serialize(obj, context)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    override fun <T : Any> deserialize(serializedBytes: SerializedBytes<T>, clazz: Class<T>): T {
        return try {
            AccessController.doPrivileged(PrivilegedExceptionAction {
                deserializationInput.deserialize(serializedBytes, clazz, context)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    override fun <T : Any> deserialize(bytes: ByteArray, clazz: Class<T>): T {
        return try {
            AccessController.doPrivileged(PrivilegedExceptionAction {
                deserializationInput.deserialize(sequence(bytes), clazz, context)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
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