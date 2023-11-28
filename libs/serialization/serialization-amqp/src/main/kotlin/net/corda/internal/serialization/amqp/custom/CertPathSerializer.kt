package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseProxySerializer
import java.io.NotSerializableException
import java.security.cert.CertPath
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory

class CertPathSerializer : BaseProxySerializer<CertPath, CertPathSerializer.CertPathProxy>() {
    override val type: Class<CertPath> get() = CertPath::class.java
    override val proxyType: Class<CertPathProxy> get() = CertPathProxy::class.java
    override val withInheritance: Boolean get() = true

    override fun toProxy(obj: CertPath): CertPathProxy = CertPathProxy(obj.type, obj.encoded)

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
            return (this === other) ||
                (other is CertPathProxy && (type == other.type && encoded.contentEquals(other.encoded)))
        }
    }
}
