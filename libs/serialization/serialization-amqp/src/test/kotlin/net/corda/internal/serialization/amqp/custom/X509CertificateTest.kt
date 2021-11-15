package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.factory
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.TypeNotation
import net.corda.internal.serialization.amqp.testutils.testSerializationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class X509CertificateTest {
    private fun generateCertificate(): X509Certificate {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val inputStream = ByteArrayInputStream(TestCertificate.r3comCert.toByteArray())
        return certificateFactory.generateCertificate(inputStream) as X509Certificate
    }

    @Test
    fun certificate() {
        serializeDeserializeAssert(generateCertificate())
    }

    @Test
    fun testSerializerIsNotRegisteredForSubclass() {
        val certificate = generateCertificate()

        val schemas = SerializationOutput(factory).serializeAndReturnSchema(certificate, testSerializationContext)
        assertThat(schemas.schema.types.map(TypeNotation::name)).doesNotContain(certificate::class.java.name)

        val serializer = factory.findCustomSerializer(certificate::class.java, certificate::class.java)
            ?: fail("No custom serializer found")
        assertThat(serializer.type).isNotSameAs(certificate::class.java)
    }
}
