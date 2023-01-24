package net.corda.configuration.rpcops.impl

import java.util.concurrent.CompletableFuture
import net.corda.configuration.read.ConfigurationGetService
import net.corda.configuration.rpcops.impl.exception.ConfigRPCOpsException
import net.corda.configuration.rpcops.impl.v1.ConfigRestResourceImpl
import net.corda.data.ExceptionEnvelope
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.httprpc.JsonObject
import net.corda.httprpc.ResponseCode
import net.corda.httprpc.exception.HttpApiException
import net.corda.httprpc.response.ResponseEntity
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.httprpc.security.RpcAuthContext
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.configuration.endpoints.v1.ConfigRestResource
import net.corda.libs.configuration.endpoints.v1.types.ConfigSchemaVersion
import net.corda.libs.configuration.endpoints.v1.types.GetConfigResponse
import net.corda.libs.configuration.endpoints.v1.types.UpdateConfigParameters
import net.corda.libs.configuration.endpoints.v1.types.UpdateConfigResponse
import net.corda.libs.configuration.validation.ConfigurationValidationException
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.v5.base.versioning.Version
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

data class TestJsonObject(override val escapedJson: String = "") : JsonObject

/**
 * Tests of [ConfigRestResourceImpl].
 */
class ConfigRestResourceImplTests {
    companion object {
        private const val actor = "test_principal"
        private const val invalidConfigError = "Invalid config"

        @Suppress("Unused")
        @JvmStatic
        @BeforeAll
        fun setRPCContext() {
            val rpcAuthContext = mock<RpcAuthContext>().apply {
                whenever(principal).thenReturn(actor)
            }
            CURRENT_RPC_CONTEXT.set(rpcAuthContext)
        }
    }

    private val req = UpdateConfigParameters("section", 999, TestJsonObject("a=b"), ConfigSchemaVersion(888, 0))
    private val successFuture = CompletableFuture.supplyAsync {
        ConfigurationManagementResponse(
            true, null, req.section, req.config.escapedJson, ConfigurationSchemaVersion(
                req.schemaVersion.major, req.schemaVersion.minor
            ), req.version
        )
    }
    private val successResponse = ResponseEntity.accepted(UpdateConfigResponse(
        req.section, req.config.escapedJson, ConfigSchemaVersion(
            req.schemaVersion.major,
            req.schemaVersion.minor
        ), req.version
    ))

    private val configSection = "section"
    private val config = Configuration("CONFIG+DEFAULT", "CONFIG", 1, ConfigurationSchemaVersion(2,3))
    private val configurationGetService = mock<ConfigurationGetService> {
        on { get(configSection) }.thenReturn(config)
    }

    @Test
    fun `createAndStartRPCSender starts new RPC sender`() {
        val (rpcSender, configRPCOps) = getConfigRPCOps()

        configRPCOps.createAndStartRPCSender(mock())

        verify(rpcSender).start()
    }

    @Test
    fun `createAndStartRPCSender closes existing RPC sender if one exists`() {
        val (rpcSender, configRPCOps) = getConfigRPCOps()

        configRPCOps.createAndStartRPCSender(mock())
        configRPCOps.createAndStartRPCSender(mock())

        verify(rpcSender).close()
    }

    @Test
    fun `updateConfig sends the correct request to the RPC sender`() {
        val rpcRequest = req.run {
            ConfigurationManagementRequest(
                section, config.escapedJson, ConfigurationSchemaVersion(
                    schemaVersion.major,
                    schemaVersion.minor
                ), actor, version
            )
        }

        val (rpcSender, configRPCOps) = getConfigRPCOps()

        configRPCOps.createAndStartRPCSender(mock())
        configRPCOps.setTimeout(1000)
        configRPCOps.updateConfig(req)

        verify(rpcSender).sendRequest(rpcRequest)
    }

    @Test
    fun `updateConfig returns HTTPUpdateConfigResponse if response is success`() {
        val (_, configRPCOps) = getConfigRPCOps()

        configRPCOps.createAndStartRPCSender(mock())
        configRPCOps.setTimeout(1000)

        val result = configRPCOps.updateConfig(req)
        assertEquals(successResponse.responseCode, result.responseCode)
        assertEquals(successResponse.responseBody, result.responseBody)
    }

    @Test
    fun `updateConfig throws if config is not valid JSON or HOCON`() {
        val invalidConfig = TestJsonObject("a=b\nc")
        val expectedMessage =
            "Configuration \"${invalidConfig.escapedJson}\" could not be validated. Valid JSON or HOCON expected. " +
                "Cause: String: 2: Key 'c' may not be followed by token: end of file"

        val (_, configRPCOps) = getConfigRPCOps(mock())
        val e = assertThrows<HttpApiException> {
            configRPCOps.updateConfig(req.copy(config = invalidConfig))
        }

        assertEquals(expectedMessage, e.message)
        assertEquals(ResponseCode.BAD_REQUEST, e.responseCode)
    }

    @Test
    fun `updateConfig throws if config fails validator`() {
        val expectedMessage = "Configuration \"a=b\" could not be validated. Valid JSON or HOCON expected. " +
                "Cause: Configuration failed to validate for key key at schema version 1.0: $invalidConfigError"

        val (_, configRPCOps) = getConfigRPCOps(mock(), true)
        val e = assertThrows<HttpApiException> {
            configRPCOps.updateConfig(req)
        }

        assertEquals(expectedMessage, e.message)
        assertEquals(ResponseCode.BAD_REQUEST, e.responseCode)
    }

    @Test
    fun `updateConfig throws HttpApiException if response is failure`() {
        val exception = ExceptionEnvelope("ErrorType", "ErrorMessage.")
        val response = req.run {
            ConfigurationManagementResponse(
                false,
                exception,
                section,
                config.escapedJson,
                ConfigurationSchemaVersion(schemaVersion.major, schemaVersion.minor),
                version
            )
        }
        val future = CompletableFuture.supplyAsync { response }

        val (_, configRPCOps) = getConfigRPCOps(future)

        configRPCOps.createAndStartRPCSender(mock())
        configRPCOps.setTimeout(1000)
        val e = assertThrows<HttpApiException> {
            configRPCOps.updateConfig(req)
        }

        assertEquals("ErrorType: ErrorMessage.", e.message)
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, e.responseCode)
    }

    @Test
    fun `updateConfig throws HttpApiException if request fails but no exception is provided`() {
        val (_, configRPCOps) = getConfigRPCOps(CompletableFuture.supplyAsync {
            ConfigurationManagementResponse(false, null, "", "", ConfigurationSchemaVersion(0, 0), 0)
        })

        configRPCOps.createAndStartRPCSender(mock())
        configRPCOps.setTimeout(1000)
        val e = assertThrows<HttpApiException> {
            configRPCOps.updateConfig(req)
        }

        assertEquals("Request was unsuccessful but no exception was provided.", e.message)
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, e.responseCode)
    }

    @Test
    fun `updateConfig throws if RPC sender is not set`() {
        val (_, configRPCOps) = getConfigRPCOps()

        configRPCOps.setTimeout(1000)
        val e = assertThrows<ConfigRPCOpsException> {
            configRPCOps.updateConfig(req)
        }

        assertEquals(
            "Configuration update request could not be sent as no RPC sender has been created.",
            e.message
        )
    }

    @Test
    fun `updateConfig throws if request timeout is not set`() {
        val (_, configRPCOps) = getConfigRPCOps()

        configRPCOps.createAndStartRPCSender(mock())
        val e = assertThrows<ConfigRPCOpsException> {
            configRPCOps.updateConfig(req)
        }

        assertEquals(
            "Configuration update request could not be sent as the request timeout has not been set.",
            e.message
        )
    }

    @Test
    fun `updateConfig throws ConfigRPCOpsServiceException if response future completes exceptionally`() {
        val future = CompletableFuture.supplyAsync<ConfigurationManagementResponse> { throw IllegalStateException() }
        val (_, configRPCOps) = getConfigRPCOps(future)

        configRPCOps.createAndStartRPCSender(mock())
        configRPCOps.setTimeout(1000)
        val e = assertThrows<ConfigRPCOpsException> {
            configRPCOps.updateConfig(req)
        }

        assertEquals("Could not publish updated configuration.", e.message)
    }

    @Test
    fun `get returns HTTPGetConfigResponse if section is found`() {
        val (_, configRPCOps) = getConfigRPCOps()

        configRPCOps.createAndStartRPCSender(mock())
        configRPCOps.setTimeout(1000)

        assertThat(GetConfigResponse(
            configSection,
            config.source,
            config.value,
            ConfigSchemaVersion(config.schemaVersion.majorVersion, config.schemaVersion.minorVersion),
            config.version
        )).isEqualTo(configRPCOps.get(configSection))
    }

    /** Returns a [ConfigRestResource] where the RPC sender returns [future] in response to any RPC requests.
     * @param future to return for any rpc requests
     * @param failValidation Set to true to cause the validator to fail validation for a request
     * @return RPCSender and ConfigRPCOpsInternal
     * */
    private fun getConfigRPCOps(
        future: CompletableFuture<ConfigurationManagementResponse> = successFuture,
        failValidation: Boolean = false
    ): Pair<RPCSender<ConfigurationManagementRequest, ConfigurationManagementResponse>, ConfigRestResourceImpl> {

        val rpcSender = mock<RPCSender<ConfigurationManagementRequest, ConfigurationManagementResponse>>().apply {
            whenever(sendRequest(any())).thenReturn(future)
        }
        val publisherFactory = mock<PublisherFactory>().apply {
            whenever(createRPCSender<ConfigurationManagementRequest, ConfigurationManagementResponse>(any(), any()))
                .thenReturn(rpcSender)
        }
        val validator = mock<ConfigurationValidator>().apply {
            if (failValidation) {
                whenever(validate(any(), any<Version>(), any(), any())).thenAnswer {
                    throw ConfigurationValidationException("key", Version(1, 0), SmartConfigImpl.empty(), setOf(invalidConfigError) )
                }
            } else {
                whenever(validate(any(),  any<Version>(), any(), any())).thenAnswer { it.arguments[2] }
            }
        }
        val validatorFactory = mock<ConfigurationValidatorFactory>().apply {
            whenever(createConfigValidator()).thenReturn(validator)
        }

        return rpcSender to ConfigRestResourceImpl(mock(), mock(), publisherFactory, validatorFactory, configurationGetService)
    }
}