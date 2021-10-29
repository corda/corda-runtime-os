package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.SerializationContext
import net.corda.internal.serialization.amqp.AMQPTypeIdentifiers
import net.corda.internal.serialization.amqp.CustomSerializer
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.Metadata
import net.corda.internal.serialization.amqp.RestrictedType
import net.corda.internal.serialization.amqp.Schema
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializationSchemas
import org.apache.qpid.proton.codec.Data
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.Type

/**
 * A serializer that writes out the content of an input stream as bytes
 * and deserializes into a [ByteArrayInputStream].
 */
object InputStreamSerializer
    : CustomSerializer.Implements<InputStream>(InputStream::class.java) {
    override val revealSubclassesInSchema: Boolean
        get() = true

    override val schemaForDocumentation = Schema(listOf(RestrictedType(
        type.toString(),
        "",
        listOf(type.toString()),
        AMQPTypeIdentifiers.primitiveTypeName(ByteArray::class.java),
        descriptor,
        emptyList()
    )))

    override fun writeDescribedObject(
        obj: InputStream,
        data: Data,
        type: Type,
        output: SerializationOutput,
        context: SerializationContext
    ) {
        data.putBinary(obj.readAllBytes())
    }

    override fun readObject(
        obj: Any,
        serializationSchemas: SerializationSchemas,
        metadata: Metadata,
        input: DeserializationInput,
        context: SerializationContext
    ) : InputStream {
        val bits = input.readObject(obj, serializationSchemas, metadata, ByteArray::class.java, context) as ByteArray
        return bits.inputStream()
    }
}
