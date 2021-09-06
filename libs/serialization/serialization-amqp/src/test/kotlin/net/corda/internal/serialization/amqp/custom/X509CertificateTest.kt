package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.custom.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class X509CertificateTest {
    @Test
    fun certificate() {
        val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509")
        val certificate: X509Certificate = certificateFactory.generateCertificate(ByteArrayInputStream(TestCertificate.r3comCert.toByteArray())) as X509Certificate

        serializeDeserializeAssert(certificate)
    }
}