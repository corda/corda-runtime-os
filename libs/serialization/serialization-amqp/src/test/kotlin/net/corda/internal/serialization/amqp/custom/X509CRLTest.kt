package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.factory
import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.TypeNotation
import net.corda.internal.serialization.amqp.testutils.testSerializationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.Test
import java.security.cert.CertificateFactory
import java.security.cert.CRL

class X509CRLTest {
    private fun generateCRL(resourceName: String): CRL {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        return this::class.java.getResource(resourceName)?.openStream()
            ?.use(certificateFactory::generateCRL)
            ?: fail("CRL $resourceName not found")
    }

    @Test
    fun empty() {
        val crl = generateCRL("empty.crl")
        serializeDeserializeAssert(crl)
    }

    @Test
    fun oneRevokedCert() {
        val crl = generateCRL("oneRevokedCert.crl")
        serializeDeserializeAssert(crl)
    }

    @Test
    fun testSerializerIsNotRegisteredForSubclass() {
        val crl = generateCRL("oneRevokedCert.crl")

        val schemas = SerializationOutput(factory).serializeAndReturnSchema(crl, testSerializationContext)
        assertThat(schemas.schema.types.map(TypeNotation::name)).doesNotContain(crl::class.java.name)

        val serializer = factory.findCustomSerializer(crl::class.java, crl::class.java)
            ?: fail("No custom serializer found")
        assertThat(serializer.type).isNotSameAs(crl::class.java)
    }
}