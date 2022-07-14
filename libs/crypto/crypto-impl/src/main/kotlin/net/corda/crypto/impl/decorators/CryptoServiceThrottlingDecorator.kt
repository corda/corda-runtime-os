package net.corda.crypto.impl.decorators

import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceExtensions
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SharedSecretSpec
import net.corda.v5.cipher.suite.SigningSpec
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.failures.CryptoException
import net.corda.v5.crypto.failures.CryptoThrottlingException
import java.security.PublicKey
import java.util.UUID

class CryptoServiceThrottlingDecorator(
    private val cryptoService: CryptoService,
) : CryptoService {
    companion object {
        private const val MAX_RETRY_GUARD: Int = 10
        private val logger = contextLogger()
    }

    private fun <R> executeWithBackingOff(block: () -> R): R {
        var attempt = 1
        var op = ""
        while(true) {
            try {
                if(attempt > 1) {
                    logger.info("Retrying operation after backing off (op={},attempt={})", op, attempt)
                }
                val result = block()
                if(attempt > 1) {
                    logger.info("Retrying after backing off succeeded (op={},attempt={})", op, attempt)
                }
                return result
            } catch (e: CryptoThrottlingException) {
                if(attempt == 1) {
                    op = UUID.randomUUID().toString()
                }
                val backoff = e.getBackoff(attempt)
                if (backoff < 0 || attempt >= MAX_RETRY_GUARD) {
                    throw CryptoException(
                        "Failed all backoff attempts (op=$op, attempt=$attempt, backoff=$backoff).",
                        e
                    )
                } else {
                    logger.warn(
                        "Throttling, backing of on attempt={}, for backoff={} (op={})",
                        attempt,
                        backoff,
                        op
                    )
                    Thread.sleep(backoff)
                }
                attempt++
            }
        }
    }

    override val extensions: List<CryptoServiceExtensions> get() =
        cryptoService.extensions

    override val supportedSchemes: Map<KeyScheme, List<SignatureSpec>> get() =
        cryptoService.supportedSchemes

    override fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean, context: Map<String, String>) =
        executeWithBackingOff {
            cryptoService.createWrappingKey(masterKeyAlias, failIfExists, context)
        }

    override fun generateKeyPair(spec: KeyGenerationSpec, context: Map<String, String>): GeneratedKey =
        executeWithBackingOff {
            cryptoService.generateKeyPair(spec, context)
        }

    override fun sign(spec: SigningSpec, data: ByteArray, context: Map<String, String>): ByteArray =
        executeWithBackingOff {
            cryptoService.sign(spec, data, context)
        }

    override fun delete(alias: String, context: Map<String, String>): Boolean =
        executeWithBackingOff {
            cryptoService.delete(alias, context)
        }

    override fun deriveSharedSecret(spec: SharedSecretSpec, context: Map<String, String>): ByteArray =
        executeWithBackingOff {
            cryptoService.deriveSharedSecret(spec, context)
        }
}