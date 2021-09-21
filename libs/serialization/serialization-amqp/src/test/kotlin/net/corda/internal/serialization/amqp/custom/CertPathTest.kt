package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.security.cert.CertPath
import java.security.cert.CertificateFactory

class CertPathTest {
    @Test
    fun certPath() {
        val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509")
        val certificate = certificateFactory.generateCertificate(ByteArrayInputStream(TestCertificate.r3comCert.toByteArray()))
        val certPath: CertPath = certificateFactory.generateCertPath(listOf(certificate))

        serializeDeserializeAssert(certPath)
    }
}

