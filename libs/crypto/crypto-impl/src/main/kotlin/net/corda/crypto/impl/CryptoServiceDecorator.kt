package net.corda.crypto.impl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceProvider
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SigningSpec
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.exceptions.CryptoServiceException
import net.corda.v5.crypto.exceptions.CryptoServiceTimeoutException
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException

class CryptoServiceDecorator(
    private val cryptoService: CryptoService,
    private val timeout: Duration,
    private val retries: Int
) : CryptoService, AutoCloseable {
    companion object {
        private val logger = contextLogger()

        private val jsonMapper = JsonMapper
            .builder()
            .enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)
            .build()
        val objectMapper = jsonMapper
            .registerModule(JavaTimeModule())
            .registerModule(KotlinModule.Builder().build())
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

        fun create(
            provider: CryptoServiceProvider<Any>,
            serviceConfig: ByteArray,
            retries: Int,
            timeout: Duration
        ): CryptoService = CryptoServiceDecorator(
            cryptoService = provider.getInstance(objectMapper.readValue(serviceConfig, provider.configType)),
            timeout = timeout,
            retries = retries
        )
    }

    @Suppress("ThrowsCount")
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

    override fun requiresWrappingKey(): Boolean {
        try {
            return cryptoService.requiresWrappingKey()
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("CryptoService operation failed", e)
        }
    }

    override fun supportedSchemes(): List<KeyScheme> {
        try {
            return cryptoService.supportedSchemes()
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("CryptoService operation failed", e)
        }
    }

    override fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean, context: Map<String, String>) {
        try {
            return executeWithTimeOut { cryptoService.createWrappingKey(masterKeyAlias, failIfExists, context) }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("CryptoService operation failed", e)
        }
    }

    override fun generateKeyPair(
        spec: KeyGenerationSpec,
        context: Map<String, String>
    ): GeneratedKey {
        try {
            return executeWithTimeOut {
                cryptoService.generateKeyPair(spec, context)
            }
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("CryptoService operation failed", e)
        }
    }

    override fun sign(
        spec: SigningSpec,
        data: ByteArray,
        context: Map<String, String>
    ): ByteArray {
        try {
            return executeWithTimeOut {
                cryptoService.sign(spec, data, context)
            }
        } catch (e: CryptoServiceException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceException("CryptoService operation failed", e)
        }
    }
}
