package net.corda.crypto.service.impl.encoding

import net.corda.crypto.service.KeyEncodingService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.cipher.suite.AbstractCipherSuite
import net.corda.v5.crypto.exceptions.CryptoException
import java.security.PublicKey

class KeyEncodingServiceImpl(
    private val suite: AbstractCipherSuite
) : KeyEncodingService {
    companion object {
        private val logger = contextLogger()
    }

    override fun decodePublicKey(encodedKey: ByteArray): PublicKey {
        logger.debug {
            "decodePublicKey(ByteArray)"
        }
        for(handler in suite.getAllKeyEncodingHandlers()) {
            val publicKey = try {
                handler.decodePublicKey(encodedKey)
            } catch (e: Throwable) {
                logger.info("Failed to decode public key, may try another handler.")
                null
            }
            if(publicKey != null) {
                return publicKey
            }
        }
        logger.error("Failed to decode public key, all handlers failed")
        throw CryptoException("Failed to decode public key, all handlers failed")
    }

    override fun decodePublicKey(encodedKey: String): PublicKey {
        logger.debug {
            "decodePublicKey(ByteArray)"
        }
        for(handler in suite.getAllKeyEncodingHandlers()) {
            val publicKey = try {
                handler.decodePublicKey(encodedKey)
            } catch (e: Throwable) {
                logger.info("Failed to decode public key, may try another handler.")
                null
            }
            if(publicKey != null) {
                return publicKey
            }
        }
        logger.error("Failed to decode public key, all handlers failed")
        throw CryptoException("Failed to decode public key, all handlers failed")
    }

    override fun encodeAsString(publicKey: PublicKey): String {
        val scheme = suite.findKeyScheme(publicKey)
        val handler = suite.findKeyEncodingHandler(scheme.codeName)
        return handler.encodeAsString(scheme, publicKey)
    }
}