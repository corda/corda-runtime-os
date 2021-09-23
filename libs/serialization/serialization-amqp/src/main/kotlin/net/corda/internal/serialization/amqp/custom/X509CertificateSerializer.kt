package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationCustomSerializer
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

object X509CertificateSerializer
    : SerializationCustomSerializer<X509Certificate, ByteArray> {
    override fun toProxy(obj: X509Certificate): ByteArray = obj.encoded
    override fun fromProxy(proxy: ByteArray): X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(proxy.inputStream()) as X509Certificate
}
