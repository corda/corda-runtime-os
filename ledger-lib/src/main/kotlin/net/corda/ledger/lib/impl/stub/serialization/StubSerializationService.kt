package net.corda.ledger.lib.impl.stub.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.internal.serialization.SerializedBytesImpl
import net.corda.internal.serialization.amqp.api.SerializationServiceInternal
import net.corda.v5.serialization.SerializedBytes
import org.apache.commons.lang3.SerializationUtils
import java.io.Serializable

class StubSerializationService : SerializationServiceInternal {
    private val objectMapper = ObjectMapper()

    override fun <T : Any> serialize(obj: T, withCompression: Boolean): SerializedBytes<T> {
        return SerializedBytesImpl(SerializationUtils.serialize(obj as Serializable))
    }

    override fun <T : Any> serialize(obj: T): SerializedBytes<T> {
        return SerializedBytesImpl(SerializationUtils.serialize(obj as Serializable))
    }

    override fun <T : Any> deserialize(serializedBytes: SerializedBytes<T>, clazz: Class<T>): T {
        return SerializationUtils.deserialize(serializedBytes.bytes)
    }

    override fun <T : Any> deserialize(bytes: ByteArray, clazz: Class<T>): T {
        return SerializationUtils.deserialize(bytes)g
    }
}