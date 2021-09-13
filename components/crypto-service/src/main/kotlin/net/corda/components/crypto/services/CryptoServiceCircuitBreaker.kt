package net.corda.components.crypto.services

import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.WrappedKeyPair
import net.corda.v5.cipher.suite.WrappedPrivateKey
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.cipher.suite.schemes.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoServiceException
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import net.corda.v5.crypto.exceptions.CryptoServiceTimeoutException
import java.security.PublicKey
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException

class CryptoServiceCircuitBreaker(private val cryptoService: CryptoService, private val timeout: Duration) : CryptoService, AutoCloseable {
    companion object {
        private val logger = contextLogger()
        private val executor = Executors.newCachedThreadPool()
    }

    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    private fun <T> executeWithTimeOut(func: () -> T): T {
        val num = UUID.randomUUID()
        try {
            logger.info("Submitting crypto task for execution (num={})...", num)
            val result = executor.submit(func).getOrThrow(timeout)
            logger.debug("Crypto task completed on time (num={})...", num)
            return result
        } catch (e: TimeoutException) {
            logger.error("Crypto task timeout (num=$num)", e)
            throw CryptoServiceTimeoutException(timeout)
        } catch (e: CryptoServiceLibraryException) {
            logger.error("Crypto task failed (num=$num)", e)
            throw e
        } catch (e: Throwable) {
            logger.error("Crypto task failed (num=$num)", e)
            throw CryptoServiceException("CryptoService operation failed", e)
        }
    }

    override fun close() {
        executor.shutdown()
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
