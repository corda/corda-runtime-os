package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationCustomSerializer
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL

object X509CRLSerializer
    : SerializationCustomSerializer<X509CRL, ByteArray> {
    override fun toProxy(obj: X509CRL): ByteArray = obj.encoded
    override fun fromProxy(proxy: ByteArray): X509CRL =
        CertificateFactory.getInstance("X.509").generateCRL(proxy.inputStream()) as X509CRL
}
