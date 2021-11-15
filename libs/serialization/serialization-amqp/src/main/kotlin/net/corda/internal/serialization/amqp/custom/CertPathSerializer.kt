package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationCustomSerializer
import java.io.NotSerializableException
import java.security.cert.CertPath
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory

class CertPathSerializer : SerializationCustomSerializer<CertPath, CertPathSerializer.CertPathProxy> {
    override fun toProxy(obj: CertPath): CertPathProxy = CertPathProxy(obj.type, obj.encoded)

    override fun fromProxy(proxy: CertPathProxy): CertPath {
        try {
            val cf = CertificateFactory.getInstance(proxy.type)
            return cf.generateCertPath(proxy.encoded.inputStream())
        } catch (ce: CertificateException) {
            throw NotSerializableException("java.security.cert.CertPath").apply { initCause(ce) }
        }
    }

    @Suppress("ArrayInDataClass")
    data class CertPathProxy(val type: String, val encoded: ByteArray)
}