package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.kryoserialization.readBytesWithLength
import net.corda.kryoserialization.writeBytesWithLength
import java.security.cert.CertPath
import java.security.cert.CertificateFactory

internal object CertPathSerializer : Serializer<CertPath>() {
    override fun read(kryo: Kryo, input: Input, type: Class<out CertPath>): CertPath {
        val factory = CertificateFactory.getInstance(input.readString())
        return factory.generateCertPath(input.readBytesWithLength().inputStream())
    }

    override fun write(kryo: Kryo, output: Output, obj: CertPath) {
        output.writeString(obj.type)
        output.writeBytesWithLength(obj.encoded)
    }
}
