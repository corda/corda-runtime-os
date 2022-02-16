package net.corda.membership.impl.httprpc

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.request.RegistrationRequest
import net.corda.data.membership.rpc.response.MembershipRpcResponse
import net.corda.data.membership.rpc.response.MembershipRpcResponseContext
import net.corda.data.membership.rpc.response.RegistrationResponse
import net.corda.data.membership.rpc.response.RegistrationStatus
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.httprpc.types.MemberRegistrationRequest
import net.corda.membership.httprpc.types.RegistrationAction
import net.corda.membership.impl.httprpc.lifecycle.MembershipRpcOpsClientLifecycleHandler
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MembershipRpcOpsClientTest {
    companion object {
        private const val REQUEST_ID = "requestId"
        private const val VIRTUAL_NODE_ID = "nodeId"
    }

    private var coordinatorIsRunning = false
    private val coordinator: LifecycleCoordinator = mock {
        on { isRunning } doAnswer { coordinatorIsRunning }
        on { start() } doAnswer { coordinatorIsRunning = true }
        on { stop() } doAnswer { coordinatorIsRunning = false }
    }

    private var lifecycleHandler: MembershipRpcOpsClientLifecycleHandler? = null

    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn coordinator
        on { createCoordinator(any(), any()) } doAnswer {
            lifecycleHandler = it.arguments[1] as MembershipRpcOpsClientLifecycleHandler
            coordinator
        }
    }

    private var rpcRequest: MembershipRpcRequest?  = null

    private val mockRPCSender: RPCSender<MembershipRpcRequest, MembershipRpcResponse> = mock {
        on { sendRequest(any()) } doAnswer {
            rpcRequest = it.arguments.first() as MembershipRpcRequest
            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(REQUEST_ID, Instant.now(), Instant.now()),
                    RegistrationResponse(
                        Instant.now(),
                        RegistrationStatus.SUBMITTED,
                        1,
                        KeyValuePairList(listOf(KeyValuePair("key", "value"))),
                        KeyValuePairList(emptyList())
                    )
                )
            )
        }
    }

    private val publisherFactory: PublisherFactory = mock {
        on {
            createRPCSender(any<RPCConfig<MembershipRpcRequest, MembershipRpcResponse>>(), any())
        } doReturn mockRPCSender
    }

    private val configurationReadService: ConfigurationReadService = mock()

    private val rpcOpsClient = MembershipRpcOpsClientImpl(
        lifecycleCoordinatorFactory,
        publisherFactory,
        configurationReadService
    )

    private val bootConfig: SmartConfig = mock()
    private val messagingConfig: SmartConfig = mock {
        on(it.withFallback(any())).thenReturn(mock())
    }

    private val configs = mapOf(
        ConfigKeys.BOOT_CONFIG to bootConfig,
        ConfigKeys.MESSAGING_CONFIG to messagingConfig
    )

    @Suppress("UNCHECKED_CAST")
    private fun setUpRpcSender() {
        // re-sets the rpc request
        rpcRequest = null
        // kicks off the MessagingConfigurationReceived event to be able to mock the rpc sender
        lifecycleHandler?.processEvent(
            ConfigChangedEvent(setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG), configs),
            coordinator
        )
    }

    private val request = MemberRegistrationRequest(VIRTUAL_NODE_ID, RegistrationAction.REQUEST_JOIN)

    @Test
    fun `starting and stopping the service succeeds`() {
        rpcOpsClient.start()
        assertTrue(rpcOpsClient.isRunning)
        rpcOpsClient.stop()
        assertFalse(rpcOpsClient.isRunning)
    }

    @Test
    fun `rpc sender sends the expected request - starting registration process`() {
        rpcOpsClient.start()
        setUpRpcSender()
        rpcOpsClient.startRegistration(request)
        rpcOpsClient.stop()

        val requestSent = rpcRequest?.request as RegistrationRequest

        assertEquals(request.virtualNodeId, requestSent.virtualNodeId)
        assertEquals(request.action.name, requestSent.registrationAction.name)
    }

    @Test
    fun `rpc sender sends the expected request - checking registration progress`() {
        rpcOpsClient.start()
        setUpRpcSender()
        rpcOpsClient.checkRegistrationProgress(request.virtualNodeId)
        rpcOpsClient.stop()

        val requestSent = rpcRequest?.request as String

        assertEquals(request.virtualNodeId, requestSent)
    }

    @Test
    fun `should fail when rpc sender is not ready`() {
        rpcOpsClient.start()
        val ex = assertFailsWith<CordaRuntimeException> { rpcOpsClient.checkRegistrationProgress(request.virtualNodeId) }
        assertEquals(
            "Failed to send request and receive response for membership RPC operation. RPC sender is not initialized.",
            ex.message
        )
        rpcOpsClient.stop()
    }

    @Test
    fun `should fail when service is not running`() {
        val ex = assertFailsWith<CordaRuntimeException> { rpcOpsClient.checkRegistrationProgress(request.virtualNodeId) }
        assertEquals(
            "MembershipRpcOpsClientImpl is not running.",
            ex.message
        )
    }

    @Test
    fun `should fail when there is an exception while sending the request`() {
        rpcOpsClient.start()
        setUpRpcSender()
        whenever(mockRPCSender.sendRequest(any())).thenThrow(CordaRPCAPISenderException("Sender exception."))
        val ex = assertFailsWith<CordaRuntimeException> { rpcOpsClient.checkRegistrationProgress(request.virtualNodeId) }
        assertEquals(
            "Failed to send request and receive response for membership RPC operation. Sender exception.",
            ex.message
        )
        rpcOpsClient.stop()
    }
}