package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.util.MapReferenceResolver
import net.corda.kryoserialization.TestCertificate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

internal class X509CertificateSerializerTest {
    @Test
    fun `serializer returns the correct X509 certificate back`() {
        val certificate = CertificateFactory
            .getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(TestCertificate.r3comCert.toByteArray())) as X509Certificate
        val kryo = Kryo(MapReferenceResolver())
        val output = Output(1600)
        X509CertificateSerializer.write(kryo, output, certificate)
        val tested = X509CertificateSerializer.read(kryo, Input(output.buffer), X509Certificate::class.java)

        assertThat(tested).isEqualTo(certificate)
    }
}
