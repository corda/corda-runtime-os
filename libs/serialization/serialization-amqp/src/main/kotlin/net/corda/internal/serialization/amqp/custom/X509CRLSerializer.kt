package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseDirectSerializer
import net.corda.serialization.InternalDirectSerializer.ReadObject
import net.corda.serialization.InternalDirectSerializer.WriteObject
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL

class X509CRLSerializer : BaseDirectSerializer<X509CRL>() {
    override val type: Class<X509CRL> get() = X509CRL::class.java
    override val withInheritance: Boolean get() = true

    override fun writeObject(obj: X509CRL, writer: WriteObject) {
        writer.putAsBytes(obj.encoded)
    }

    override fun readObject(reader: ReadObject): X509CRL {
        val bytes = reader.getAsBytes()
        return CertificateFactory.getInstance("X.509").generateCRL(bytes.inputStream()) as X509CRL
    }
}
