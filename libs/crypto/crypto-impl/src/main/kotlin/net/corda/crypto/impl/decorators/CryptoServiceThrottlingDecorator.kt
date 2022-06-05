package net.corda.crypto.impl.decorators

import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SigningSpec
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.exceptions.CSLThrottlingException
import net.corda.v5.crypto.exceptions.CryptoServiceException
import java.util.UUID

class CryptoServiceThrottlingDecorator(
    private val cryptoService: CryptoService,
) : CryptoService {
    companion object {
        private val logger = contextLogger()
    }

    private fun <R> executeWithBackingOff(block: () -> R): R {
        var backOffTime = 0L
        var attempt = 1
        var opId = ""
        while(true) {
            try {
                if(attempt > 1) {
                    logger.info("Retrying operation after backing off (opId={},attempt={})", opId, attempt)
                }
                val result = block()
                if(attempt > 1) {
                    logger.info("Retrying after backing off succeeded (opId={},attempt={})", opId, attempt)
                }
                return result
            } catch (e: CSLThrottlingException) {
                if(attempt == 1) {
                    opId = UUID.randomUUID().toString()
                }
                backOffTime = e.getBackoff(attempt, backOffTime)
                if (backOffTime < 0) {
                    throw CryptoServiceException(
                        "Failed all backoff attempts (opId=$opId, attempt=$attempt, backOffTime=$backOffTime).",
                        e,
                        isRecoverable = false
                    )
                } else {
                    logger.warn(
                        "Throttling, backing of on attempt={}, for backOffTime={} (opId={})",
                        attempt,
                        backOffTime,
                        opId
                    )
                    Thread.sleep(backOffTime)
                }
                attempt++
            }
        }
    }

    override fun requiresWrappingKey(): Boolean =
        cryptoService.requiresWrappingKey()

    override fun supportedSchemes(): List<KeyScheme> =
        cryptoService.supportedSchemes()

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
}