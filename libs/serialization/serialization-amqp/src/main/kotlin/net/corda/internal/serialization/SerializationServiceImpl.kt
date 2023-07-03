package net.corda.internal.serialization

import net.corda.base.internal.sequence
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.serialization.SerializationContext
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.serialization.SerializedBytes
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
                deserializationInput.deserialize(serializedBytes.unwrap(), clazz, context)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    override fun <T : Any> deserialize(bytes: ByteArray, clazz: Class<T>): T {
        return try {
            AccessController.doPrivileged(PrivilegedExceptionAction {
                deserializationInput.deserialize(bytes.sequence(), clazz, context)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }
}
