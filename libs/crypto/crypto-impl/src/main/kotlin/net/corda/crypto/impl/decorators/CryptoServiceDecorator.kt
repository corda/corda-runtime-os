package net.corda.crypto.impl.decorators

import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.CryptoServiceExtensions
import net.corda.crypto.cipher.suite.GeneratedKey
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.SharedSecretSpec
import net.corda.crypto.cipher.suite.SigningSpec
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.core.isRecoverable
import net.corda.crypto.impl.retrying.BackoffStrategy
import net.corda.crypto.impl.retrying.CryptoRetryingExecutorWithTimeout
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoException
import org.slf4j.LoggerFactory
import java.time.Duration

class CryptoServiceDecorator(
    private val cryptoService: CryptoService,
    private val attemptTimeout: Duration,
    private val maxAttempts: Int
) : CryptoService, AutoCloseable {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        fun create(
            cryptoService: CryptoService,
            maxAttempts: Int,
            attemptTimeout: Duration
        ): CryptoService = CryptoServiceDecorator(
            cryptoService = CryptoServiceThrottlingDecorator(cryptoService),
            attemptTimeout = attemptTimeout,
            maxAttempts = maxAttempts
        )
    }

    private val withTimeout = CryptoRetryingExecutorWithTimeout(
        logger,
        BackoffStrategy.createBackoff(maxAttempts, listOf(100L)),
        attemptTimeout
    )

    override fun close() {
        (cryptoService as? AutoCloseable)?.close()
    }

    override val extensions: List<CryptoServiceExtensions>
        get() = try {
            cryptoService.extensions
        } catch (e: RuntimeException) {
            if(e.isRecoverable()) {
                throw CryptoException("Calling extensions failed", e)
            } else {
                throw e
            }
        } catch (e: Throwable) {
            throw CryptoException("Calling extensions failed", e)
        }

    override val supportedSchemes: Map<KeyScheme, List<SignatureSpec>>
        get() = try {
            cryptoService.supportedSchemes
        } catch (e: RuntimeException) {
            if(e.isRecoverable()) {
                throw CryptoException("Calling supportedSchemes failed", e)
            } else {
                throw e
            }
        } catch (e: Throwable) {
            throw CryptoException("Calling supportedSchemes failed", e)
        }

    override fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean, context: Map<String, String>) = try {
        withTimeout.executeWithRetry {
            cryptoService.createWrappingKey(masterKeyAlias, failIfExists, context)
        }
    } catch (e: RuntimeException) {
        if(e.isRecoverable()) {
            throw CryptoException(
                "Calling createWrappingKey failed (masterKeyAlias=$masterKeyAlias,failIfExists=$failIfExists)",
                e
            )
        } else {
            throw e
        }
    } catch (e: Throwable) {
        throw CryptoException(
            "Calling createWrappingKey failed (masterKeyAlias=$masterKeyAlias,failIfExists=$failIfExists)",
            e
        )
    }

    override fun generateKeyPair(
        spec: KeyGenerationSpec,
        context: Map<String, String>
    ): GeneratedKey = try {
        withTimeout.executeWithRetry {
            cryptoService.generateKeyPair(spec, context)
        }
    } catch (e: RuntimeException) {
        if(e.isRecoverable()) {
            throw CryptoException("Calling generateKeyPair failed (spec=$spec)", e)
        } else {
            throw e
        }
    } catch (e: Throwable) {
        throw CryptoException("Calling generateKeyPair failed (spec=$spec)", e)
    }

    override fun sign(
        spec: SigningSpec,
        data: ByteArray,
        context: Map<String, String>
    ): ByteArray = try {
        withTimeout.executeWithRetry {
            cryptoService.sign(spec, data, context)
        }
    } catch (e: RuntimeException) {
        if(e.isRecoverable()) {
            throw CryptoException("Calling sign failed (spec=$spec,data.size=${data.size})", e)
        } else {
            throw e
        }
    } catch (e: Throwable) {
        throw CryptoException("Calling sign failed (spec=$spec,data.size=${data.size})", e)
    }

    override fun delete(alias: String, context: Map<String, String>): Boolean = try {
        withTimeout.executeWithRetry {
            cryptoService.delete(alias, context)
        }
    } catch (e: RuntimeException) {
        if(e.isRecoverable()) {
            throw CryptoException("Calling delete failed (alias=$alias)", e)
        } else {
            throw e
        }
    } catch (e: Throwable) {
        throw CryptoException("Calling delete failed (alias=$alias)", e)
    }

    override fun deriveSharedSecret(spec: SharedSecretSpec, context: Map<String, String>): ByteArray = try {
        withTimeout.executeWithRetry {
            cryptoService.deriveSharedSecret(spec, context)
        }
    } catch (e: RuntimeException) {
        if(e.isRecoverable()) {
            throw CryptoException("Calling deriveSharedSecret failed (spec=$spec)", e)
        } else {
            throw e
        }
    } catch (e: Throwable) {
        throw CryptoException("Calling deriveSharedSecret failed (spec=$spec)", e)
    }
}
