package net.corda.crypto.impl.decorators

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.crypto.impl.CryptoRetryingExecutorWithTimeout
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceExtensions
import net.corda.v5.cipher.suite.CryptoServiceProvider
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SigningSpec
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoServiceException
import java.time.Duration

class CryptoServiceDecorator(
    private val cryptoService: CryptoService,
    private val attemptTimeout: Duration,
    private val maxAttempts: Int
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
            maxAttempts: Int,
            attemptTimeout: Duration
        ): CryptoService = CryptoServiceDecorator(
            cryptoService = CryptoServiceThrottlingDecorator(
                provider.getInstance(objectMapper.readValue(serviceConfig, provider.configType))
            ),
            attemptTimeout = attemptTimeout,
            maxAttempts = maxAttempts
        )
    }

    private val withTimeout = CryptoRetryingExecutorWithTimeout(logger, maxAttempts, attemptTimeout)

    override fun close() {
        (cryptoService as? AutoCloseable)?.close()
    }

    override val extensions: List<CryptoServiceExtensions> get() = try {
        cryptoService.extensions
    } catch (e: Throwable) {
        throw CryptoServiceException("CryptoService extensions failed", e, isRecoverable = false)
    }

    override val supportedSchemes: Map<KeyScheme, List<SignatureSpec>> get() = try {
        cryptoService.supportedSchemes
    } catch (e: Throwable) {
        throw CryptoServiceException("CryptoService supportedSchemes failed", e, isRecoverable = false)
    }

    override fun createWrappingKey(masterKeyAlias: String, failIfExists: Boolean, context: Map<String, String>) = try {
        withTimeout.executeWithRetry {
            cryptoService.createWrappingKey(masterKeyAlias, failIfExists, context)
        }
    } catch (e: Throwable) {
        throw CryptoServiceException(
                "CryptoService createWrappingKey failed (masterKeyAlias=$masterKeyAlias,failIfExists=$failIfExists)",
                e,
                isRecoverable = false
            )
    }

    override fun generateKeyPair(
        spec: KeyGenerationSpec,
        context: Map<String, String>
    ): GeneratedKey = try {
        withTimeout.executeWithRetry {
            cryptoService.generateKeyPair(spec, context)
        }
    } catch (e: Throwable) {
        throw CryptoServiceException(
            "CryptoService generateKeyPair failed (spec=$spec)",
            e,
            isRecoverable = false
        )
    }

    override fun sign(
        spec: SigningSpec,
        data: ByteArray,
        context: Map<String, String>
    ): ByteArray = try {
        withTimeout.executeWithRetry {
            cryptoService.sign(spec, data, context)
        }
    } catch (e: Throwable) {
        throw CryptoServiceException(
            "CryptoService sign failed (spec=$spec,data.size=${data.size})",
            e,
            isRecoverable = false
        )
    }

    override fun delete(alias: String, context: Map<String, String>) = try {
        withTimeout.executeWithRetry {
            cryptoService.delete(alias, context)
        }
    } catch (e: Throwable) {
        throw CryptoServiceException(
            "CryptoService delete failed (alias=$alias)",
            e,
            isRecoverable = false
        )
    }
}
