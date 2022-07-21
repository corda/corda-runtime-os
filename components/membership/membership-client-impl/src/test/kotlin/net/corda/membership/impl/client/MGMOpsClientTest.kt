package net.corda.membership.impl.client

import java.util.concurrent.CompletableFuture
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.rpc.request.MGMGroupPolicyRequest
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.response.MGMGroupPolicyResponse
import net.corda.data.membership.rpc.response.MembershipRpcResponse
import net.corda.data.membership.rpc.response.MembershipRpcResponseContext
import net.corda.data.membership.rpc.response.RegistrationRpcStatus
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.impl.EndpointInfoImpl
import net.corda.membership.lib.impl.MemberInfoFactoryImpl
import net.corda.membership.lib.impl.converter.EndpointInfoConverter
import net.corda.membership.lib.impl.converter.PublicKeyConverter
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.EndpointInfo
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions
import java.security.PublicKey
import java.time.Instant
import java.util.*

class MGMOpsClientTest {
    companion object {
        private val holdingIdentity = HoldingIdentity("CN=Alice,O=Alice,OU=Unit1,L=London,ST=State1,C=GB", "DEFAULT_MEMBER_GROUP_ID")
        private const val HOLDING_IDENTITY_STRING = "test"
        private const val KNOWN_KEY = "12345"

        val mgmX500Name = MemberX500Name.parse("CN=Alice,OU=Unit1,O=Alice,L=London,ST=State1,C=GB")
        private val clock = TestClock(Instant.ofEpochSecond(100))
    }

    private var virtualNodeInfoReadService: VirtualNodeInfoReadService= mock {
        on { getById(HOLDING_IDENTITY_STRING) } doReturn VirtualNodeInfo(
            holdingIdentity,
            CpiIdentifier("test", "test", SecureHash("algorithm", "1234".toByteArray())),
            null, UUID.randomUUID(), null, UUID.randomUUID(),
            timestamp = Instant.now()
        )
    }

    private val knownKey: PublicKey = mock()
    private val keys = listOf(knownKey, knownKey)

    private val endpoints = listOf(
        EndpointInfoImpl("https://corda5.r3.com:10000", EndpointInfo.DEFAULT_PROTOCOL_VERSION),
        EndpointInfoImpl("https://corda5.r3.com:10001", 10)
    )

    private val keyEncodingService: CipherSchemeMetadata = mock {
        on { decodePublicKey(KNOWN_KEY) } doReturn knownKey
        on { encodeAsString(knownKey) } doReturn KNOWN_KEY
    }

    private val converters = listOf(
        EndpointInfoConverter(),
        PublicKeyConverter(keyEncodingService)
    )

    private val memberInfoFactory = MemberInfoFactoryImpl(LayeredPropertyMapMocks.createFactory(converters))

    private val alice = createMemberInfo("CN=Alice,OU=Unit1,O=Alice,L=London,ST=State1,C=GB")

    @Suppress("SpreadOperator")
    private fun createMemberInfo(name: String): MemberInfo = memberInfoFactory.create(
        sortedMapOf(
            MemberInfoExtension.PARTY_NAME to name,
            MemberInfoExtension.PARTY_SESSION_KEY to KNOWN_KEY,
            MemberInfoExtension.GROUP_ID to "DEFAULT_MEMBER_GROUP_ID",
            *convertPublicKeys().toTypedArray(),
            *convertEndpoints().toTypedArray(),
            MemberInfoExtension.SOFTWARE_VERSION to "5.0.0",
            MemberInfoExtension.PLATFORM_VERSION to "10",
            MemberInfoExtension.SERIAL to "1",
            "corda.group.protocol.registration" to "corda.group.protocol.registration",
            "corda.group.protocol.synchronisation" to "corda.group.protocol.synchronisation",
            "corda.group.truststore.session" to "corda.group.truststore.session",
            "corda.group.pki.session" to "corda.group.pki.session",
            "corda.group.pki.tls" to "corda.group.pki.tls",
            "corda.group.protocol.p2p.mode" to "corda.group.protocol.p2p.mode",
            "corda.session.key" to "corda.session.key",
            "corda.ecdh.key" to "corda.ecdh.key",
            "corda.endpoints.0.connectionURL" to "corda.endpoints.0.connectionURL",
            "corda.endpoints.0.protocolVersion" to "1"
        ),
        sortedMapOf(
            MemberInfoExtension.STATUS to MemberInfoExtension.MEMBER_STATUS_ACTIVE,
            MemberInfoExtension.MODIFIED_TIME to clock.instant().toString(),
            IS_MGM to "true"
        )
    )

    private fun convertPublicKeys(): List<Pair<String, String>> =
        keys.mapIndexed { index, ledgerKey ->
            String.format(
                MemberInfoExtension.LEDGER_KEYS_KEY,
                index
            ) to keyEncodingService.encodeAsString(ledgerKey)
        }

    private fun convertEndpoints(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        for (index in endpoints.indices) {
            result.add(
                Pair(
                    String.format(MemberInfoExtension.URL_KEY, index),
                    endpoints[index].url
                )
            )
            result.add(
                Pair(
                    String.format(MemberInfoExtension.PROTOCOL_VERSION, index),
                    endpoints[index].protocolVersion.toString()
                )
            )
        }
        return result
    }

    private val groupReader: MembershipGroupReader = mock {
        on { lookup(eq(mgmX500Name)) } doReturn alice
    }

    private val membershipGroupReaderProvider: MembershipGroupReaderProvider = mock {
        on { getGroupReader(any()) } doReturn groupReader
    }

    private val componentHandle: RegistrationHandle = mock()
    private val configHandle: AutoCloseable = mock()

    private var coordinatorIsRunning = false
    private val coordinator: LifecycleCoordinator = mock {
        on { isRunning } doAnswer { coordinatorIsRunning }
        on { start() } doAnswer { coordinatorIsRunning = true }
        on { stop() } doAnswer { coordinatorIsRunning = false }
        on { followStatusChangesByName(any()) } doReturn componentHandle
    }

    private var lifecycleHandler: LifecycleEventHandler? = null

    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doAnswer {
            lifecycleHandler = it.arguments[1] as LifecycleEventHandler
            coordinator
        }
    }

    private var rpcRequest: MembershipRpcRequest? = null

    private lateinit var rpcSender: RPCSender<MembershipRpcRequest, MembershipRpcResponse>

    private lateinit var publisherFactory: PublisherFactory

    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(any(), any()) } doReturn configHandle
    }

    private lateinit var mgmOpsClient: MGMOpsClientImpl

    private val messagingConfig: SmartConfig = mock()
    private val bootConfig: SmartConfig = mock ()

    private val configs = mapOf(
        ConfigKeys.BOOT_CONFIG to bootConfig,
        ConfigKeys.MESSAGING_CONFIG to messagingConfig
    )

    fun startComponent() = lifecycleHandler?.processEvent(StartEvent(), coordinator)
    fun stopComponent() = lifecycleHandler?.processEvent(StopEvent(), coordinator)
    fun changeRegistrationStatus(status: LifecycleStatus) = lifecycleHandler?.processEvent(
        RegistrationStatusChangeEvent(mock(), status), coordinator
    )

    fun changeConfig() = lifecycleHandler?.processEvent(
        ConfigChangedEvent(setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG), configs),
        coordinator
    )

    @Suppress("UNCHECKED_CAST")
    private fun setUpRpcSender() {
        // re-sets the rpc request
        rpcRequest = null
        // kicks off the MessagingConfigurationReceived event to be able to mock the rpc sender
        changeConfig()
    }

    @BeforeEach
    fun setUp() {
        rpcSender = mock {
            on { sendRequest(any()) } doAnswer {
                rpcRequest = it.arguments.first() as MembershipRpcRequest
                CompletableFuture.completedFuture(
                    MembershipRpcResponse(
                        MembershipRpcResponseContext(
                            rpcRequest!!.requestContext.requestId,
                            rpcRequest!!.requestContext.requestTimestamp,
                            clock.instant()
                        ),
                        MGMGroupPolicyResponse(
                            "groupPolicy"
                        )
                    )
                )
            }
        }

        publisherFactory = mock {
            on {
                createRPCSender(
                    any<RPCConfig<MembershipRpcRequest, MembershipRpcResponse>>(),
                    any()
                )
            } doReturn rpcSender
        }

        mgmOpsClient = MGMOpsClientImpl(
            lifecycleCoordinatorFactory,
            publisherFactory,
            configurationReadService,
            membershipGroupReaderProvider,
            virtualNodeInfoReadService
        )
    }

    @Test
    fun `starting and stopping the service succeeds`() {
        mgmOpsClient.start()
        assertTrue(mgmOpsClient.isRunning)
        mgmOpsClient.stop()
        assertFalse(mgmOpsClient.isRunning)
    }

    @Test
    fun `rpc sender sends the expected request - starting generate group policy process`() {
        mgmOpsClient.start()
        setUpRpcSender()
        mgmOpsClient.generateGroupPolicy(HOLDING_IDENTITY_STRING)
        mgmOpsClient.stop()

        val requestSent = rpcRequest?.request as MGMGroupPolicyRequest

        Assertions.assertThat(requestSent.holdingIdentityId).isEqualTo(HOLDING_IDENTITY_STRING)
    }

    @Test
    fun `should fail when rpc sender is not ready`() {
        mgmOpsClient.start()
        val ex = assertFailsWith<IllegalStateException> {
            mgmOpsClient.generateGroupPolicy(HOLDING_IDENTITY_STRING)
        }
        assertTrue { ex.message!!.contains("incorrect state") }
        mgmOpsClient.stop()
    }

    @Test
    fun `should fail when service is not running`() {
        val ex = assertFailsWith<IllegalStateException> {
            mgmOpsClient.generateGroupPolicy(HOLDING_IDENTITY_STRING)
        }
        assertTrue { ex.message!!.contains("incorrect state") }
    }

    @Test
    fun `should fail when there is an RPC sender exception while sending the request`() {
        mgmOpsClient.start()
        setUpRpcSender()
        val message = "Sender exception."
        whenever(rpcSender.sendRequest(any())).thenThrow(CordaRPCAPISenderException(message))
        val ex = assertFailsWith<CordaRuntimeException> {
            mgmOpsClient.generateGroupPolicy(HOLDING_IDENTITY_STRING)
        }
        assertTrue { ex.message!!.contains(message) }
        mgmOpsClient.stop()
    }

    @Test
    fun `should fail when response is null`() {
        mgmOpsClient.start()
        setUpRpcSender()

        whenever(rpcSender.sendRequest(any())).then {
            rpcRequest = it.arguments.first() as MembershipRpcRequest
            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(
                        rpcRequest!!.requestContext.requestId,
                        rpcRequest!!.requestContext.requestTimestamp,
                        clock.instant()
                    ),
                    null
                )
            )
        }

        val ex = assertFailsWith<CordaRuntimeException> {
            mgmOpsClient.generateGroupPolicy(HOLDING_IDENTITY_STRING)
        }
        assertTrue { ex.message!!.contains("null") }
        mgmOpsClient.stop()
    }

    @Test
    fun `should fail when request and response has different ids`() {
        mgmOpsClient.start()
        setUpRpcSender()

        whenever(rpcSender.sendRequest(any())).then {
            rpcRequest = it.arguments.first() as MembershipRpcRequest
            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(
                        "wrongId",
                        rpcRequest!!.requestContext.requestTimestamp,
                        clock.instant()
                    ),
                    MGMGroupPolicyResponse(
                        "groupPolicy"
                    )
                )
            )
        }

        val ex = assertFailsWith<CordaRuntimeException> {
            mgmOpsClient.generateGroupPolicy(HOLDING_IDENTITY_STRING)
        }
        assertTrue { ex.message!!.contains("ID") }
        mgmOpsClient.stop()
    }

    @Test
    fun `should fail when request and response has different requestTimestamp`() {
        mgmOpsClient.start()
        setUpRpcSender()

        whenever(rpcSender.sendRequest(any())).then {
            rpcRequest = it.arguments.first() as MembershipRpcRequest
            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(
                        rpcRequest!!.requestContext.requestId,
                        clock.instant().plusMillis(10000000),
                        clock.instant()
                    ),
                    MGMGroupPolicyResponse(
                        "groupPolicy"
                    )
                )
            )
        }

        val ex = assertFailsWith<CordaRuntimeException> {
            mgmOpsClient.generateGroupPolicy(HOLDING_IDENTITY_STRING)
        }
        assertTrue { ex.message!!.contains("timestamp") }
        mgmOpsClient.stop()
    }

    @Test
    fun `should fail when response type is not the expected type`() {
        mgmOpsClient.start()
        setUpRpcSender()

        whenever(rpcSender.sendRequest(any())).then {
            rpcRequest = it.arguments.first() as MembershipRpcRequest
            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(
                        rpcRequest!!.requestContext.requestId,
                        rpcRequest!!.requestContext.requestTimestamp,
                        clock.instant()
                    ),
                    "WRONG RESPONSE TYPE"
                )
            )
        }

        val ex = assertFailsWith<CordaRuntimeException> {
            mgmOpsClient.generateGroupPolicy(HOLDING_IDENTITY_STRING)
        }
        assertTrue { ex.message!!.contains("Expected class") }
        mgmOpsClient.stop()
    }

    @Test
    fun `start event starts following the statuses of the required dependencies`() {
        startComponent()

        verify(coordinator).followStatusChangesByName(
            eq(setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
                LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()))
        )
    }

    @Test
    fun `start event closes dependency handle if it exists`() {
        startComponent()
        startComponent()

        verify(componentHandle).close()
    }

    @Test
    fun `stop event doesn't closes handles before they are created`() {
        stopComponent()

        verify(componentHandle, never()).close()
        verify(configHandle, never()).close()
    }

    @Test
    fun `component handle is created after starting and closed when stopping`() {
        startComponent()
        stopComponent()

        verify(componentHandle).close()
    }

    @Test
    fun `config handle is created after registration status changes to UP and closed when stopping`() {
        changeRegistrationStatus(LifecycleStatus.UP)
        stopComponent()

        verify(configHandle).close()
    }

    @Test
    fun `registration status UP registers for config updates`() {
        changeRegistrationStatus(LifecycleStatus.UP)

        verify(configurationReadService).registerComponentForUpdates(
            any(), any()
        )
        verify(coordinator, never()).updateStatus(eq(LifecycleStatus.UP), any())
    }

    @Test
    fun `registration status DOWN sets component status to DOWN`() {
        startComponent()
        changeRegistrationStatus(LifecycleStatus.UP)
        changeRegistrationStatus(LifecycleStatus.DOWN)

        verify(configHandle).close()
        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
    }

    @Test
    fun `registration status ERROR sets component status to DOWN`() {
        startComponent()
        changeRegistrationStatus(LifecycleStatus.UP)
        changeRegistrationStatus(LifecycleStatus.ERROR)

        verify(configHandle).close()
        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
    }

    @Test
    fun `registration status DOWN closes config handle if status was previously UP`() {
        startComponent()
        changeRegistrationStatus(LifecycleStatus.UP)

        verify(configurationReadService).registerComponentForUpdates(
            any(), any()
        )

        changeRegistrationStatus(LifecycleStatus.DOWN)

        verify(configHandle).close()
    }

    @Test
    fun `registration status UP closes config handle if status was previously UP`() {
        changeRegistrationStatus(LifecycleStatus.UP)

        verify(configurationReadService).registerComponentForUpdates(
            any(), any()
        )

        changeRegistrationStatus(LifecycleStatus.UP)

        verify(configHandle).close()
    }

    @Test
    fun `after receiving the messaging configuration the rpc sender is initialized`() {
        changeConfig()
        verify(publisherFactory).createRPCSender(any<RPCConfig<MembershipRpcRequest, MembershipRpcResponse>>(), any())
        verify(rpcSender).start()
        verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())
    }

}