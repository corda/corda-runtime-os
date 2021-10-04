package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class X509CertificateTest {
    @Test
    fun certificate() {
        val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509")
        val inputStream = ByteArrayInputStream(TestCertificate.r3comCert.toByteArray())
        val certificate: X509Certificate = certificateFactory.generateCertificate(inputStream) as X509Certificate

        serializeDeserializeAssert(certificate)
    }
}