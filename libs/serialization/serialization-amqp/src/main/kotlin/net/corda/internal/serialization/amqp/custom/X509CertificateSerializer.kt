package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseDirectSerializer
import net.corda.serialization.InternalDirectSerializer.ReadObject
import net.corda.serialization.InternalDirectSerializer.WriteObject
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class X509CertificateSerializer : BaseDirectSerializer<X509Certificate>() {
    override val type: Class<X509Certificate> get() = X509Certificate::class.java
    override val withInheritance: Boolean get() = true

    override fun writeObject(obj: X509Certificate, writer: WriteObject) {
        writer.putAsBytes(obj.encoded)
    }

    override fun readObject(reader: ReadObject): X509Certificate {
        val bits = reader.getAsBytes()
        return CertificateFactory.getInstance("X.509").generateCertificate(bits.inputStream()) as X509Certificate
    }
}
