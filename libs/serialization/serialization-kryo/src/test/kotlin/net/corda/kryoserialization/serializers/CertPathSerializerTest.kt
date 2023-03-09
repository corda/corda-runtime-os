package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.util.MapReferenceResolver
import net.corda.kryoserialization.TestCertificate
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.security.cert.CertPath
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

internal class CertPathSerializerTest {
    @Test
    fun `serializer returns the correct cert path back`() {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certificate = certificateFactory
            .generateCertificate(ByteArrayInputStream(TestCertificate.r3comCert.toByteArray())) as X509Certificate
        val certPath = certificateFactory.generateCertPath(listOf(certificate))
        val kryo = Kryo(MapReferenceResolver())
        val output = Output(1600)
        CertPathSerializer.write(kryo, output, certPath)
        val tested = CertPathSerializer.read(kryo, Input(output.buffer), CertPath::class.java)

        Assertions.assertThat(tested).isEqualTo(certPath)
    }
}
