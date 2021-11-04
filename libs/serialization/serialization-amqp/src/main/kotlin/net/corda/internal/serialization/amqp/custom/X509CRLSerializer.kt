package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.CustomSerializer
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.Metadata
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializationSchemas
import net.corda.serialization.SerializationContext
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL

object X509CRLSerializer
    : CustomSerializer.Implements<X509CRL>(X509CRL::class.java) {

    override fun writeDescribedObject(obj: X509CRL, data: Data, type: Type, output: SerializationOutput,
                                      context: SerializationContext) {
        output.writeObject(obj.encoded, data, this.type, context)
    }

    override fun readObject(
        obj: Any,
        serializationSchemas: SerializationSchemas,
        metadata: Metadata,
        input: DeserializationInput,
        context: SerializationContext
    ): X509CRL {
        val bytes = input.readObject(obj, serializationSchemas, metadata, ByteArray::class.java, context) as ByteArray
        return CertificateFactory.getInstance("X.509").generateCRL(bytes.inputStream()) as X509CRL
    }
}
