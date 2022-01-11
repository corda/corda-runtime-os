package net.corda.configuration.rpcops.impl.tests

import net.corda.configuration.rpcops.ConfigRPCOpsServiceException
import net.corda.configuration.rpcops.impl.v1.ConfigRPCOps
import net.corda.configuration.rpcops.impl.v1.ConfigRPCOpsImpl
import net.corda.configuration.rpcops.impl.v1.types.HTTPUpdateConfigRequest
import net.corda.configuration.rpcops.impl.v1.types.HTTPUpdateConfigResponse
import net.corda.data.ExceptionEnvelope
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.httprpc.exception.HttpApiException
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.httprpc.security.RpcAuthContext
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import org.junit.jupiter.api.Assertions.assertEquals
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

    private val request = HTTPUpdateConfigRequest("section", 999, "a=b", 888)
    private val config = Configuration(request.config, request.version.toString())
    private val successFuture = CompletableFuture.supplyAsync { ConfigurationManagementResponse(config) }
    private val successResponse = HTTPUpdateConfigResponse(config.toString())

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
        val (rpcSender, configRPCOps) = getConfigRPCOps()

        configRPCOps.createAndStartRPCSender(mock())
        configRPCOps.setTimeout(1000)
        configRPCOps.updateConfig(request)

        verify(rpcSender).sendRequest(request.toRPCRequest(actor))
    }

    @Test
    fun `updateConfig returns HTTPUpdateConfigResponse if response is success`() {
        val (_, configRPCOps) = getConfigRPCOps()

        configRPCOps.createAndStartRPCSender(mock())
        configRPCOps.setTimeout(1000)

        assertEquals(successResponse, configRPCOps.updateConfig(request))
    }

    @Test
    fun `updateConfig throws HttpApiException if response is failure`() {
        val response = ConfigurationManagementResponse(ExceptionEnvelope("ErrorType", "ErrorMessage."))
        val future = CompletableFuture.supplyAsync { response }

        val (_, configRPCOps) = getConfigRPCOps(future)

        configRPCOps.createAndStartRPCSender(mock())
        configRPCOps.setTimeout(1000)
        val e = assertThrows<HttpApiException> {
            configRPCOps.updateConfig(request)
        }

        assertEquals("ErrorType: ErrorMessage.", e.message)
        assertEquals(500, e.statusCode)
    }

    @Test
    fun `updateConfig throws HttpApiException if response is unrecognised`() {
        val (_, configRPCOps) = getConfigRPCOps(CompletableFuture.supplyAsync { ConfigurationManagementResponse(Any()) })

        configRPCOps.createAndStartRPCSender(mock())
        configRPCOps.setTimeout(1000)
        val e = assertThrows<HttpApiException> {
            configRPCOps.updateConfig(request)
        }

        assertEquals("Unexpected response type: class java.lang.Object", e.message)
        assertEquals(500, e.statusCode)
    }

    @Test
    fun `updateConfig throws if RPC sender is not set`() {
        val configRPCOps = ConfigRPCOpsImpl(mock())

        configRPCOps.setTimeout(1000)
        val e = assertThrows<ConfigRPCOpsServiceException> {
            configRPCOps.updateConfig(mock())
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
            configRPCOps.updateConfig(mock())
        }

        assertEquals(
            "Configuration update request could not be sent as the request timeout has not been set.",
            e.message
        )
    }

    @Test
    fun `updateConfig throws ConfigRPCOpsServiceException if response future completes exceptionally`() {
        val future = CompletableFuture.supplyAsync<ConfigurationManagementResponse> { throw Exception() }
        val (_, configRPCOps) = getConfigRPCOps(future)

        configRPCOps.createAndStartRPCSender(mock())
        configRPCOps.setTimeout(1000)
        val e = assertThrows<ConfigRPCOpsServiceException> {
            configRPCOps.updateConfig(request)
        }

        assertEquals("Could not publish updated configuration.", e.message)
    }

    /** Returns a [ConfigRPCOps] where the RPC sender returns [future] in response to any RPC requests. */
    private fun getConfigRPCOps(
        future: CompletableFuture<ConfigurationManagementResponse> = successFuture
    ): Pair<RPCSender<ConfigurationManagementRequest, ConfigurationManagementResponse>, ConfigRPCOps> {

        val rpcSender = mock<RPCSender<ConfigurationManagementRequest, ConfigurationManagementResponse>>().apply {
            whenever(sendRequest(any())).thenReturn(future)
        }
        val publisherFactory = mock<PublisherFactory>().apply {
            whenever(createRPCSender<ConfigurationManagementRequest, ConfigurationManagementResponse>(any(), any()))
                .thenReturn(rpcSender)
        }
        return rpcSender to ConfigRPCOpsImpl(publisherFactory)
    }
}