package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
import java.security.cert.CertificateFactory
import kotlin.test.assertNotNull

class X509CRLTest {
    @Test
    fun empty() {
        val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509")

        val resource = X509CRLTest::class.java.getResource("empty.crl")
        assertNotNull(resource)
        resource.openStream().use {
            val crl = certificateFactory.generateCRL(it)
            serializeDeserializeAssert(crl)
        }
    }

    @Test
    fun oneRevokedCert() {
        val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509")

        val resource = X509CRLTest::class.java.getResource("oneRevokedCert.crl")
        assertNotNull(resource)
        resource.openStream().use {
            val crl = certificateFactory.generateCRL(it)
            serializeDeserializeAssert(crl)
        }
    }
}