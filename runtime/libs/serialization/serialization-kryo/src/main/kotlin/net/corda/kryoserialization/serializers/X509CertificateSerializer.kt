package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.kryoserialization.readBytesWithLength
import net.corda.kryoserialization.writeBytesWithLength
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

internal object X509CertificateSerializer : Serializer<X509Certificate>() {
    override fun read(kryo: Kryo, input: Input, type: Class<out X509Certificate>): X509Certificate {
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(input.readBytesWithLength().inputStream()) as X509Certificate
    }

    override fun write(kryo: Kryo, output: Output, obj: X509Certificate) {
        output.writeBytesWithLength(obj.encoded)
    }
}
