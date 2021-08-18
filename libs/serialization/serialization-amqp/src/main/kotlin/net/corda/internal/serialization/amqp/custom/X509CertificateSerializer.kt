package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationContext
import net.corda.internal.serialization.amqp.AMQPTypeIdentifiers
import net.corda.internal.serialization.amqp.CustomSerializer
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.Metadata
import net.corda.internal.serialization.amqp.RestrictedType
import net.corda.internal.serialization.amqp.Schema
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializationSchemas
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

object X509CertificateSerializer
    : CustomSerializer.Implements<X509Certificate>(
        X509Certificate::class.java
) {
    override val schemaForDocumentation = Schema(listOf(RestrictedType(
            type.toString(),
            "",
            listOf(type.toString()),
            AMQPTypeIdentifiers.primitiveTypeName(ByteArray::class.java),
            descriptor,
            emptyList()
    )))

    override fun writeDescribedObject(obj: X509Certificate, data: Data, type: Type, output: SerializationOutput,
                                      context: SerializationContext) {
        output.writeObject(obj.encoded, data, clazz, context)
    }

    override fun readObject(obj: Any, serializationSchemas: SerializationSchemas, metadata: Metadata,
                            input: DeserializationInput, context: SerializationContext): X509Certificate {
        val bits = input.readObject(obj, serializationSchemas, metadata, ByteArray::class.java, context) as ByteArray
        return CertificateFactory.getInstance("X.509").generateCertificate(bits.inputStream()) as X509Certificate
    }
}
