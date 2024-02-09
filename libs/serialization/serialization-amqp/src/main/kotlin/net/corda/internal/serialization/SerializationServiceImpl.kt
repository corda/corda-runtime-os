package net.corda.internal.serialization

import net.corda.base.internal.sequence
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.api.SerializationServiceInternal
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.serialization.SerializationContext
import net.corda.v5.serialization.SerializedBytes
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction

class SerializationServiceImpl(
    private val outputFactory: SerializerFactory,
    private val inputFactory: SerializerFactory,
    private val contextWithCompression: SerializationContext,
    private val contextWithoutCompression: SerializationContext
) : SerializationServiceInternal {

    override fun <T : Any> serialize(obj: T): SerializedBytes<T> {
        return try {
            @Suppress("deprecation", "removal")
            java.security.AccessController.doPrivileged(PrivilegedExceptionAction {
                SerializationOutput(outputFactory).serialize(obj, contextWithCompression)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    override fun <T : Any> serialize(obj: T, withCompression: Boolean): SerializedBytes<T> {
        return try {
            @Suppress("deprecation", "removal")
            java.security.AccessController.doPrivileged(PrivilegedExceptionAction {
                if (withCompression) {
                    SerializationOutput(outputFactory).serialize(obj, contextWithCompression)
                } else {
                    SerializationOutput(outputFactory).serialize(obj, contextWithoutCompression)
                }
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    override fun <T : Any> deserialize(serializedBytes: SerializedBytes<T>, clazz: Class<T>): T {
        return try {
            @Suppress("deprecation", "removal")
            java.security.AccessController.doPrivileged(PrivilegedExceptionAction {
                DeserializationInput(inputFactory).deserialize(serializedBytes.unwrap(), clazz, contextWithCompression)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    override fun <T : Any> deserialize(bytes: ByteArray, clazz: Class<T>): T {
        return try {
            @Suppress("deprecation", "removal")
            java.security.AccessController.doPrivileged(PrivilegedExceptionAction {
                DeserializationInput(inputFactory).deserialize(bytes.sequence(), clazz, contextWithCompression)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }
}
