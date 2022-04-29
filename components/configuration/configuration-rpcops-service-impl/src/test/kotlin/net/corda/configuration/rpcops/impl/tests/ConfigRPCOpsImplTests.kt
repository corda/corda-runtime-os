package net.corda.configuration.rpcops.impl.tests

import net.corda.configuration.rpcops.ConfigRPCOpsServiceException
import net.corda.configuration.rpcops.impl.v1.ConfigRPCOpsImpl
import net.corda.configuration.rpcops.impl.v1.ConfigRPCOpsInternal
import net.corda.data.ExceptionEnvelope
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.httprpc.ResponseCode
import net.corda.httprpc.exception.HttpApiException
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.httprpc.security.RpcAuthContext
import net.corda.libs.configuration.endpoints.v1.types.HTTPUpdateConfigRequest
import net.corda.libs.configuration.endpoints.v1.types.HTTPUpdateConfigResponse
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.v5.base.versioning.Version
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture

/** Tests of [ConfigRPCOpsImpl]. */
class ConfigRPCOpsImplTests {
    companion object {
        private const val actor = "test_principal"

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

    private val req = HTTPUpdateConfigRequest("section", 999, "a=b", Version(888, 0))
    private val successFuture = CompletableFuture.supplyAsync {
        ConfigurationManagementResponse(
            true, null, req.section, req.config, ConfigurationSchemaVersion(
                req.schemaVersion.major, req.schemaVersion.minor
            ), req.version
        )
    }
    private val successResponse = HTTPUpdateConfigResponse(
        req.section, req.config, Version(
            req.schemaVersion.major,
            req.schemaVersion.minor
        ), req.version
    )

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
    fun `stop closes existing RPC sender if one exists`() {
        val (rpcSender, configRPCOps) = getConfigRPCOps()

        configRPCOps.createAndStartRPCSender(mock())
        configRPCOps.stop()

        verify(rpcSender).close()
    }

    @Test
    fun `updateConfig sends the correct request to the RPC sender`() {
        val rpcRequest = req.run {
            ConfigurationManagementRequest(
                section, config, ConfigurationSchemaVersion(
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

        assertEquals(successResponse, configRPCOps.updateConfig(req))
    }

    @Test
    fun `updateConfig throws if config is not valid JSON or HOCON`() {
        val invalidConfig = "a=b\nc"
        val expectedMessage = "Configuration \"$invalidConfig\" could not be validated. Valid JSON or HOCON expected. " +
                "Cause: String: 2: Key 'c' may not be followed by token: end of file"

        val (_, configRPCOps) = getConfigRPCOps(mock())
        val e = assertThrows<HttpApiException> {
            configRPCOps.updateConfig(req.copy(config = invalidConfig))
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
                config,
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
        val e = assertThrows<ConfigRPCOpsServiceException> {
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
        val e = assertThrows<ConfigRPCOpsServiceException> {
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
        val e = assertThrows<ConfigRPCOpsServiceException> {
            configRPCOps.updateConfig(req)
        }

        assertEquals("Could not publish updated configuration.", e.message)
    }

    @Test
    fun `is not running if RPC sender is not created`() {
        val configRPCOps = ConfigRPCOpsImpl(mock(), mock())
        configRPCOps.setTimeout(0)
        assertFalse(configRPCOps.isRunning)
    }

    @Test
    fun `is not running if RPC timeout is not set`() {
        val (_, configRPCOps) = getConfigRPCOps()
        configRPCOps.createAndStartRPCSender(mock())
        assertFalse(configRPCOps.isRunning)
    }

    @Test
    fun `is running if RPC sender is created and RPC timeout is set`() {
        val (_, configRPCOps) = getConfigRPCOps()
        configRPCOps.createAndStartRPCSender(mock())
        configRPCOps.setTimeout(0)
        assertTrue(configRPCOps.isRunning)
    }

    /** Returns a [ConfigRPCOpsInternal] where the RPC sender returns [future] in response to any RPC requests. */
    private fun getConfigRPCOps(
        future: CompletableFuture<ConfigurationManagementResponse> = successFuture
    ): Pair<RPCSender<ConfigurationManagementRequest, ConfigurationManagementResponse>, ConfigRPCOpsInternal> {

        val rpcSender = mock<RPCSender<ConfigurationManagementRequest, ConfigurationManagementResponse>>().apply {
            whenever(sendRequest(any())).thenReturn(future)
        }
        val publisherFactory = mock<PublisherFactory>().apply {
            whenever(createRPCSender<ConfigurationManagementRequest, ConfigurationManagementResponse>(any(), any()))
                .thenReturn(rpcSender)
        }
        val validator = mock<ConfigurationValidator>().apply {
            whenever(validate(any(), any(), any(), any())).thenAnswer { it.arguments[2] }
        }
        val validatorFactory = mock<ConfigurationValidatorFactory>().apply {
            whenever(createConfigValidator()).thenReturn(validator)
        }
        return rpcSender to ConfigRPCOpsImpl(publisherFactory, validatorFactory)
    }
}