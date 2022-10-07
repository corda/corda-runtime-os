package net.corda.crypto.service.impl.encoding

import net.corda.crypto.service.KeyEncodingService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSuiteBase
import net.corda.v5.crypto.publicKeyId
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.util.io.pem.PemReader
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.io.StringReader
import java.security.PublicKey

@Component(service = [KeyEncodingService::class])
class KeyEncodingServiceImpl @Activate constructor(
    @Reference(service = CipherSuiteBase::class)
    private val suite: CipherSuiteBase
) : KeyEncodingService {
    companion object {
        private val logger = contextLogger()
    }

    override fun decode(encodedKey: ByteArray): PublicKey {
        var publicKey = quickDecode(encodedKey)
        if(publicKey != null) {
            return publicKey
        }
        for (handler in suite.getAllKeyEncodingHandlers()) {
            publicKey = try {
                handler.decode(encodedKey)
            } catch (e: Throwable) {
                logger.info("Failed to decode public key, may try another handler.")
                null
            }
            if (publicKey != null) {
                return publicKey
            }
        }
        logger.error("Failed to decode public key, all handlers failed")
        throw IllegalArgumentException("Failed to decode public key, all handlers failed")
    }

    override fun decodePem(encodedKey: String): PublicKey {
        var publicKey = quickDecodePem(encodedKey)
        if(publicKey != null) {
            return publicKey
        }
        for(handler in suite.getAllKeyEncodingHandlers()) {
            publicKey = try {
                handler.decodePem(encodedKey)
            } catch (e: Throwable) {
                logger.info("Failed to decode public key, may try another handler.")
                null
            }
            if(publicKey != null) {
                return publicKey
            }
        }
        logger.error("Failed to decode public key, all handlers failed")
        throw IllegalArgumentException("Failed to decode public key, all handlers failed")
    }

    override fun encodeAsPem(publicKey: PublicKey): String {
        val scheme = suite.findKeyScheme(publicKey)
            ?: throw IllegalArgumentException("The key ${publicKey.publicKeyId()} is not supported.")
        val handler = suite.findKeyEncodingHandler(scheme.codeName)
            ?: throw IllegalArgumentException("The encoding handler is not found for ${publicKey.publicKeyId()}.")
        return handler.encodeAsPem(scheme, publicKey)
    }

    private fun quickDecode(encodedKey: ByteArray): PublicKey? {
        try {
            val publicKeyInfo = SubjectPublicKeyInfo.getInstance(encodedKey)
            val scheme = suite.findKeyScheme(publicKeyInfo.algorithm)
                ?: return null
            return suite.findKeyEncodingHandler(scheme.codeName)?.decode(
                scheme,
                publicKeyInfo,
                encodedKey
            )
        } catch (e: Throwable) {
            return null
        }
    }

    private fun quickDecodePem(encodedKey: String): PublicKey? {
        try {
            val pemContent = parsePemContent(encodedKey)
            val publicKeyInfo = SubjectPublicKeyInfo.getInstance(pemContent)
            val scheme = suite.findKeyScheme(publicKeyInfo.algorithm)
                ?: return null
            return suite.findKeyEncodingHandler(scheme.codeName)?.decodePem(
                scheme,
                publicKeyInfo,
                pemContent
            )
        } catch (e: Throwable) {
            return null
        }
    }

    private fun parsePemContent(pem: String): ByteArray =
        StringReader(pem).use { strReader ->
            return PemReader(strReader).use { pemReader ->
                pemReader.readPemObject().content
            }
        }
}