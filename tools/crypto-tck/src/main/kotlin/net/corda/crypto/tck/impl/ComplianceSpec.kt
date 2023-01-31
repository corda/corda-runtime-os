package net.corda.crypto.tck.impl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.impl.decorators.CryptoServiceDecorator
import net.corda.crypto.tck.ExecutionOptions
import net.corda.v5.base.types.toHexString
import net.corda.v5.crypto.sha256Bytes
import java.util.UUID

class ComplianceSpec(
    val options: ExecutionOptions
) {
    companion object {
        private val jsonMapper = JsonMapper
            .builder()
            .enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)
            .build()
        private val objectMapper: ObjectMapper = jsonMapper
            .registerModule(JavaTimeModule())
            .registerModule(KotlinModule.Builder().build())
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    fun createService(providers: CryptoServiceProviderMap): CryptoService {
        val cryptoService = providers.get(options.serviceName).getInstance(options.serviceConfig)
        return CryptoServiceDecorator.create(
            cryptoService = cryptoService,
            maxAttempts = options.maxAttempts,
            attemptTimeout = options.attemptTimeout
        )
    }

    fun generateRandomIdentifier(len: Int = 12) =
        UUID.randomUUID().toString().toByteArray().sha256Bytes().toHexString().take(len)
}