package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.SerializationContext
import net.corda.internal.serialization.amqp.CustomSerializer
import net.corda.internal.serialization.amqp.SerializerFactory
import java.io.NotSerializableException
import java.security.cert.CertPath
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory

class CertPathSerializer(
    factory: SerializerFactory
) : CustomSerializer.Proxy<CertPath, CertPathSerializer.CertPathProxy>(
    CertPath::class.java,
    CertPathProxy::class.java,
    factory,
    withInheritance = true
) {
    override fun toProxy(obj: CertPath, context: SerializationContext): CertPathProxy = CertPathProxy(obj.type, obj.encoded)

    override fun fromProxy(proxy: CertPathProxy): CertPath {
        try {
            val cf = CertificateFactory.getInstance(proxy.type)
            return cf.generateCertPath(proxy.encoded.inputStream())
        } catch (ce: CertificateException) {
            val nse = NotSerializableException("java.security.cert.CertPath: $type")
            nse.initCause(ce)
            throw nse
        }
    }

    data class CertPathProxy(val type: String, val encoded: ByteArray) {
        override fun hashCode() = (type.hashCode() * 31) + encoded.contentHashCode()
        override fun equals(other: Any?): Boolean {
            return (this === other)
                || (other is CertPathProxy && (type == other.type && encoded.contentEquals(other.encoded)))
        }
    }
}
