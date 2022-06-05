package net.corda.crypto.impl.decorators

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.crypto.impl.ExecutorWithTimeout
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceProvider
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SigningSpec
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.exceptions.CryptoServiceException
import java.time.Duration

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
        val objectMapper: ObjectMapper = jsonMapper
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
            cryptoService = CryptoServiceThrottlingDecorator(
                provider.getInstance(objectMapper.readValue(serviceConfig, provider.configType))
            ),
            timeout = timeout,
            retries = retries
        )
    }

    private val withTimeout = ExecutorWithTimeout(logger, retries, timeout)

    override fun close() {
        (cryptoService as? AutoCloseable)?.close()
    }

    override fun requiresWrappingKey(): Boolean = try {
        cryptoService.requiresWrappingKey()
    } catch (e: CryptoServiceException) {
        throw e
    } catch (e: Throwable) {
        throw CryptoServiceException("CryptoService operation failed", e)
    }

    override fun supportedSchemes(): List<KeyScheme> = try {
        cryptoService.supportedSchemes()
    } catch (e: CryptoServiceException) {
        throw e
    } catch (e: Throwable) {
        throw CryptoServiceException("CryptoService operation failed", e)
    }

    override fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean, context: Map<String, String>) = try {
        withTimeout.executeWithRetry {
            cryptoService.createWrappingKey(masterKeyAlias, failIfExists, context)
        }
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: CryptoServiceException) {
        throw e
    } catch (e: Throwable) {
        throw CryptoServiceException("CryptoService operation failed", e)
    }

    override fun generateKeyPair(
        spec: KeyGenerationSpec,
        context: Map<String, String>
    ): GeneratedKey = try {
        withTimeout.executeWithRetry {
            cryptoService.generateKeyPair(spec, context)
        }
    } catch (e: CryptoServiceException) {
        throw e
    } catch (e: Throwable) {
        throw CryptoServiceException("CryptoService operation failed", e)
    }

    override fun sign(
        spec: SigningSpec,
        data: ByteArray,
        context: Map<String, String>
    ): ByteArray = try {
        withTimeout.executeWithRetry {
            cryptoService.sign(spec, data, context)
        }
    } catch (e: CryptoServiceException) {
        throw e
    } catch (e: Throwable) {
        throw CryptoServiceException("CryptoService operation failed", e)
    }
}
