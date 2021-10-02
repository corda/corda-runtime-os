package net.corda.crypto.impl

import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.WrappedKeyPair
import net.corda.v5.cipher.suite.WrappedPrivateKey
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoServiceException
import net.corda.v5.crypto.exceptions.CryptoServiceTimeoutException
import java.security.PublicKey
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException

class CryptoServiceDecorator(
    private val cryptoService: CryptoService,
    private val timeout: Duration,
    private val retries: Long
) : CryptoService, AutoCloseable {
    companion object {
        private val logger = contextLogger()
    }

    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    private fun <T> executeWithTimeOut(func: () -> T): T {
        var retry = retries
        val num = UUID.randomUUID()
        while (true) {
            try {
                logger.info("Submitting crypto task for execution (num={})...", num)
                val result = CompletableFuture.supplyAsync(func).getOrThrow(timeout)
                logger.debug("Crypto task completed on time (num={})...", num)
                return result
            } catch (e: TimeoutException) {
                retry--
                if (retry < 0) {
                    logger.error("Crypto task timeout (num=$num), all retries are exhausted", e)
                    throw CryptoServiceTimeoutException(timeout, e)
                } else {
                    logger.error("Crypto task timeout (num=$num), will retry...", e)
                }
            } catch (e: Throwable) {
                logger.error("Crypto task failed (num=$num)", e)
                throw e
            }
        }
    }

    override fun close() {
        (cryptoService as? AutoCloseable)?.close()
    }

    @Suppress("TooGenericExceptionCaught")
    override fun requiresWrappingKey(): Boolean {
        try {
            return cryptoService.requiresWrappingKey()
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("CryptoService operation failed", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun supportedSchemes(): Array<SignatureScheme> {
        try {
            return cryptoService.supportedSchemes()
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("CryptoService operation failed", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun supportedWrappingSchemes(): Array<SignatureScheme> {
        try {
            return cryptoService.supportedWrappingSchemes()
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("CryptoService operation failed", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun containsKey(alias: String): Boolean {
        try {
            return executeWithTimeOut { cryptoService.containsKey(alias) }
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("CryptoService operation failed", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun findPublicKey(alias: String): PublicKey? {
        try {
            return executeWithTimeOut { cryptoService.findPublicKey(alias) }
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("CryptoService operation failed", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean) {
        try {
            return executeWithTimeOut { cryptoService.createWrappingKey(masterKeyAlias, failIfExists) }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("CryptoService operation failed", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun generateKeyPair(alias: String, signatureScheme: SignatureScheme): PublicKey {
        try {
            return executeWithTimeOut { cryptoService.generateKeyPair(alias, signatureScheme) }
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("CryptoService operation failed", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun generateWrappedKeyPair(masterKeyAlias: String, wrappedSignatureScheme: SignatureScheme): WrappedKeyPair {
        try {
            return executeWithTimeOut { cryptoService.generateWrappedKeyPair(masterKeyAlias, wrappedSignatureScheme) }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("CryptoService operation failed", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun sign(alias: String, signatureScheme: SignatureScheme, data: ByteArray): ByteArray {
        try {
            return executeWithTimeOut { cryptoService.sign(alias, signatureScheme, data) }
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("CryptoService operation failed", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun sign(alias: String, signatureScheme: SignatureScheme, signatureSpec: SignatureSpec, data: ByteArray): ByteArray {
        try {
            return executeWithTimeOut { cryptoService.sign(alias, signatureScheme, signatureSpec, data) }
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("CryptoService operation failed", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun sign(wrappedKey: WrappedPrivateKey, signatureSpec: SignatureSpec, data: ByteArray): ByteArray {
        try {
            return executeWithTimeOut { cryptoService.sign(wrappedKey, signatureSpec, data) }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("CryptoService operation failed", e)
        }
    }
}
