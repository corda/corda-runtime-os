package net.corda.membership.impl.client

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.ShortHash
import net.corda.crypto.impl.converter.PublicKeyConverter
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentGroupParameters
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedData
import net.corda.data.membership.actions.request.DistributeGroupParameters
import net.corda.data.membership.actions.request.DistributeMemberInfo
import net.corda.data.membership.actions.request.MembershipActionsRequest
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.data.membership.preauth.PreAuthTokenStatus
import net.corda.data.membership.rpc.request.MGMGroupPolicyRequest
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.response.MGMGroupPolicyResponse
import net.corda.data.membership.rpc.response.MembershipRpcResponse
import net.corda.data.membership.rpc.response.MembershipRpcResponseContext
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
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.client.CouldNotFindEntityException
import net.corda.membership.client.MemberNotAnMgmException
import net.corda.membership.client.ServiceNotReadyException
import net.corda.membership.lib.ContextDeserializationException
import net.corda.membership.lib.EndpointInfoFactory
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.EPOCH_KEY
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.id
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.approval.ApprovalRuleParams
import net.corda.membership.lib.impl.MemberInfoFactoryImpl
import net.corda.membership.lib.impl.converter.EndpointInfoConverter
import net.corda.membership.lib.impl.converter.MemberNotaryDetailsConverter
import net.corda.membership.lib.registration.DECLINED_REASON_FOR_USER_GENERAL_MANUAL_DECLINED
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceOperation
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.TestClock
import net.corda.utilities.concurrent.getOrThrow
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MGMResourceClientTest {
    private companion object {
        const val HOLDING_IDENTITY_STRING = "1234567890AB"
        const val KNOWN_KEY = "12345"
        const val RULE_REGEX = "rule-regex"
        const val RULE_LABEL = "rule-label"
        const val RULE_ID = "rule-id"
        const val REQUEST_ID = "b305129b-8c92-4092-b3a2-e6d452ce2b01"
        const val SERIAL = 1L
        const val REASON = "test"
        const val DEFAULT_MEMBER_GROUP_ID = "DEFAULT_MEMBER_GROUP_ID"

        val RULE_TYPE = ApprovalRuleType.STANDARD
        val memberName = MemberX500Name.parse("CN=Bob,O=Bob,OU=Unit1,L=London,ST=State1,C=GB")
        val mgmX500Name = MemberX500Name.parse("CN=Alice,OU=Unit1,O=Alice,L=London,ST=State1,C=GB")
        val holdingIdentity = createTestHoldingIdentity(mgmX500Name.toString(), DEFAULT_MEMBER_GROUP_ID)
        val shortHash = ShortHash.of(HOLDING_IDENTITY_STRING)
        val clock = TestClock(Instant.ofEpochSecond(100))

        fun String.uuid(): UUID = UUID.fromString(this)
    }

    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { getByHoldingIdentityShortHash(shortHash) } doReturn VirtualNodeInfo(
            holdingIdentity,
            CpiIdentifier("test", "test", SecureHashImpl("algorithm", "1234".toByteArray())),
            null,
            UUID(0, 1),
            null,
            UUID(1, 2),
            null,
            UUID(3, 1),
            timestamp = Instant.ofEpochSecond(1)
        )
    }

    private val knownKey: PublicKey = mock()
    private val keys = listOf(knownKey, knownKey)

    private val endpointInfoFactory: EndpointInfoFactory = mock {
        on { create(any(), any()) } doAnswer { invocation ->
            mock {
                on { this.url } doReturn invocation.getArgument(0)
                on { this.protocolVersion } doReturn invocation.getArgument(1)
            }
        }
    }
    private val endpoints = listOf(
        endpointInfoFactory.create("https://corda5.r3.com:10000"),
        endpointInfoFactory.create("https://corda5.r3.com:10001", 10)
    )

    private val keyEncodingService: CipherSchemeMetadata = mock {
        on { decodePublicKey(KNOWN_KEY) } doReturn knownKey
        on { encodeAsString(knownKey) } doReturn KNOWN_KEY
    }

    private val converters = listOf(
        EndpointInfoConverter(),
        MemberNotaryDetailsConverter(keyEncodingService),
        PublicKeyConverter(keyEncodingService)
    )
    private val unitOperation = mock<MembershipPersistenceOperation<Unit>> {
        on { execute() } doReturn MembershipPersistenceResult.success()
    }

    private val contextBytes = byteArrayOf(0)
    private val memberContext = SignedData(ByteBuffer.wrap(contextBytes), mock(), mock())
    private val keyValuePairListSerializer = mock<CordaAvroSerializer<KeyValuePairList>>()
    private val keyValuePairListDeserializer = mock<CordaAvroDeserializer<KeyValuePairList>> {
        on { deserialize(contextBytes) } doReturn KeyValuePairList(listOf(KeyValuePair(PARTY_NAME, memberName.toString())))
    }
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on {
            createAvroDeserializer(
                any(),
                eq(KeyValuePairList::class.java)
            )
        } doReturn keyValuePairListDeserializer
        on {
            createAvroSerializer<KeyValuePairList>(any())
        } doReturn keyValuePairListSerializer
    }
    private val memberInfoFactory = MemberInfoFactoryImpl(
        LayeredPropertyMapMocks.createFactory(converters),
        cordaAvroSerializationFactory
    )
    private class Operation<T>(
        private val result: MembershipPersistenceResult<T>,
    ) : MembershipPersistenceOperation<T> {
        override fun execute() = result

        override fun createAsyncCommands() = emptyList<Record<*, *>>()
    }

    private val alice = createMemberInfo(mgmX500Name.toString())
    private val bob = createMemberInfo(memberName.toString(), isMgm = false)

    @Suppress("SpreadOperator")
    private fun createMemberInfo(name: String, isMgm: Boolean = true): MemberInfo = memberInfoFactory.createMemberInfo(
        sortedMapOf(
            PARTY_NAME to name,
            String.format(MemberInfoExtension.PARTY_SESSION_KEYS, 0) to KNOWN_KEY,
            MemberInfoExtension.GROUP_ID to DEFAULT_MEMBER_GROUP_ID,
            *convertPublicKeys().toTypedArray(),
            *convertEndpoints().toTypedArray(),
            MemberInfoExtension.SOFTWARE_VERSION to "5.0.0",
            MemberInfoExtension.PLATFORM_VERSION to "5000",
        ),
        sortedMapOf(
            MemberInfoExtension.STATUS to MemberInfoExtension.MEMBER_STATUS_ACTIVE,
            MemberInfoExtension.MODIFIED_TIME to clock.instant().toString(),
            IS_MGM to isMgm.toString(),
            MemberInfoExtension.SERIAL to "1",
        )
    )
    private val memberInfo = mock<MemberInfo> {
        on { memberProvidedContext } doReturn mock()
        on { mgmProvidedContext } doReturn mock()
        on { serial } doReturn 0
    }
    private val mockMemberInfoFactory = mock<MemberInfoFactory> {
        on { createMemberInfo(any()) } doReturn memberInfo
    }

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
        on { lookup(eq(mgmX500Name), any()) } doReturn alice
        on { lookup(mgmX500Name) } doReturn alice
        on { lookup(eq(memberName), any()) } doReturn bob
    }

    private val membershipGroupReaderProvider: MembershipGroupReaderProvider = mock {
        on { getGroupReader(any()) } doReturn groupReader
    }

    private val componentHandle: RegistrationHandle = mock()
    private val configHandle: Resource = mock()

    private var coordinatorIsRunning = false
    private val coordinator: LifecycleCoordinator = mock {
        on { isRunning } doAnswer { coordinatorIsRunning }
        on { start() } doAnswer { coordinatorIsRunning = true }
        on { stop() } doAnswer { coordinatorIsRunning = false }
        on { followStatusChangesByName(any()) } doReturn componentHandle
        on { createManagedResource(any(), any<() -> Resource>()) } doAnswer {
            val function: () -> Resource = it.getArgument(1)
            function.invoke()
        }
    }

    private var lifecycleHandler: LifecycleEventHandler? = null

    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doAnswer {
            lifecycleHandler = it.arguments[1] as LifecycleEventHandler
            coordinator
        }
    }

    private val rpcRequest = argumentCaptor<MembershipRpcRequest>()

    private val rpcSender = mock<RPCSender<MembershipRpcRequest, MembershipRpcResponse>>()
    private val publisher = mock<Publisher>()

    private val publisherFactory = mock<PublisherFactory> {
        on {
            createRPCSender(
                any<RPCConfig<MembershipRpcRequest, MembershipRpcResponse>>(),
                any()
            )
        } doReturn rpcSender
        on { createPublisher(any(), any()) } doReturn publisher
    }

    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(any(), any()) } doReturn configHandle
    }
    private val membershipPersistenceClient = mock<MembershipPersistenceClient>()
    private val membershipQueryClient = mock<MembershipQueryClient>()
    private val mgmResourceClient = MGMResourceClientImpl(
        lifecycleCoordinatorFactory,
        publisherFactory,
        configurationReadService,
        membershipGroupReaderProvider,
        virtualNodeInfoReadService,
        membershipPersistenceClient,
        membershipQueryClient,
        mockMemberInfoFactory,
        keyEncodingService,
        cordaAvroSerializationFactory,
    )

    private val messagingConfig: SmartConfig = mock()
    private val bootConfig: SmartConfig = mock()

    private val configs = mapOf(
        ConfigKeys.BOOT_CONFIG to bootConfig,
        ConfigKeys.MESSAGING_CONFIG to messagingConfig
    )

    private fun startComponent() = lifecycleHandler?.processEvent(StartEvent(), coordinator)
    private fun stopComponent() = lifecycleHandler?.processEvent(StopEvent(), coordinator)
    private fun changeRegistrationStatus(status: LifecycleStatus) = lifecycleHandler?.processEvent(
        RegistrationStatusChangeEvent(mock(), status),
        coordinator
    )

    private fun changeConfig() = lifecycleHandler?.processEvent(
        ConfigChangedEvent(setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG), configs),
        coordinator
    )

    private fun setUpRpcSender(response: Any?) {
        whenever(rpcSender.sendRequest(rpcRequest.capture())).doAnswer {
            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(
                        rpcRequest.lastValue.requestContext.requestId,
                        rpcRequest.lastValue.requestContext.requestTimestamp,
                        clock.instant()
                    ),
                    response,
                )
            )
        }

        // kicks off the MessagingConfigurationReceived event to be able to mock the rpc sender
        changeConfig()
    }

    @Test
    fun `rpc sender sends the expected request - starting generate group policy process`() {
        mgmResourceClient.start()
        setUpRpcSender(
            MGMGroupPolicyResponse(
                "groupPolicy",
            )
        )
        mgmResourceClient.generateGroupPolicy(shortHash)
        mgmResourceClient.stop()

        val requestSent = rpcRequest.firstValue.request as? MGMGroupPolicyRequest

        assertThat(requestSent?.holdingIdentityId).isEqualTo(HOLDING_IDENTITY_STRING)
    }

    @Test
    fun `should fail when rpc sender is not ready`() {
        mgmResourceClient.start()
        val ex = assertFailsWith<IllegalStateException> {
            mgmResourceClient.generateGroupPolicy(shortHash)
        }
        assertTrue { ex.message!!.contains("incorrect state") }
        mgmResourceClient.stop()
    }

    @Test
    fun `should fail when service is not running`() {
        val ex = assertFailsWith<IllegalStateException> {
            mgmResourceClient.generateGroupPolicy(shortHash)
        }
        assertTrue { ex.message!!.contains("incorrect state") }
    }

    @Test
    fun `should fail when there is an RPC sender exception while sending the request`() {
        mgmResourceClient.start()
        changeConfig()
        val message = "Sender exception."
        whenever(rpcSender.sendRequest(any())).doThrow(CordaRPCAPISenderException(message))
        val ex = assertFailsWith<CordaRuntimeException> {
            mgmResourceClient.generateGroupPolicy(shortHash)
        }
        assertTrue { ex.message!!.contains(message) }
        mgmResourceClient.stop()
    }

    @Test
    fun `should fail when response is null`() {
        mgmResourceClient.start()
        setUpRpcSender(null)

        val ex = assertFailsWith<CordaRuntimeException> {
            mgmResourceClient.generateGroupPolicy(shortHash)
        }
        assertTrue { ex.message!!.contains("null") }
        mgmResourceClient.stop()
    }

    @Test
    fun `should fail when request and response has different ids`() {
        mgmResourceClient.start()
        changeConfig()

        whenever(rpcSender.sendRequest(rpcRequest.capture())).doAnswer {
            val request = rpcRequest.lastValue
            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(
                        "wrongId",
                        request.requestContext.requestTimestamp,
                        clock.instant()
                    ),
                    MGMGroupPolicyResponse(
                        "groupPolicy"
                    )
                )
            )
        }

        val ex = assertFailsWith<CordaRuntimeException> {
            mgmResourceClient.generateGroupPolicy(shortHash)
        }
        assertTrue { ex.message!!.contains("ID") }
        mgmResourceClient.stop()
    }

    @Test
    fun `should fail when request and response has different requestTimestamp`() {
        mgmResourceClient.start()
        changeConfig()
        whenever(rpcSender.sendRequest(rpcRequest.capture())).doAnswer {
            val request = it.getArgument<MembershipRpcRequest>(0)
            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(
                        request.requestContext.requestId,
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
            mgmResourceClient.generateGroupPolicy(shortHash)
        }
        assertTrue { ex.message!!.contains("timestamp") }
        mgmResourceClient.stop()
    }

    @Test
    fun `should fail when the sender get an error`() {
        mgmResourceClient.start()
        changeConfig()
        val future = CompletableFuture.failedFuture<MembershipRpcResponse>(TimeoutException())
        whenever(rpcSender.sendRequest(rpcRequest.capture())).doReturn(future)

        assertThatThrownBy {
            mgmResourceClient.generateGroupPolicy(shortHash)
        }.isInstanceOf(ServiceNotReadyException::class.java)

        mgmResourceClient.stop()
    }

    @Test
    fun `should fail when response type is not the expected type`() {
        mgmResourceClient.start()
        setUpRpcSender("WRONG RESPONSE TYPE")

        val ex = assertFailsWith<CordaRuntimeException> {
            mgmResourceClient.generateGroupPolicy(shortHash)
        }

        assertTrue { ex.message!!.contains("Expected class") }
        mgmResourceClient.stop()
    }

    @Test
    fun `generateGroupPolicy should fail if the member can not be found`() {
        mgmResourceClient.start()
        setUpRpcSender(
            MGMGroupPolicyResponse(
                "groupPolicy",
            )
        )

        assertThrows<CouldNotFindEntityException> {
            mgmResourceClient.generateGroupPolicy(ShortHash.of("000000000000"))
        }
        mgmResourceClient.stop()
    }

    @Test
    fun `generateGroupPolicy should fail if the member can not be read`() {
        mgmResourceClient.start()
        setUpRpcSender(
            MGMGroupPolicyResponse(
                "groupPolicy",
            )
        )
        whenever(groupReader.lookup(mgmX500Name)).doReturn(null)

        assertThrows<CouldNotFindEntityException> {
            mgmResourceClient.generateGroupPolicy(shortHash)
        }
        mgmResourceClient.stop()
    }

    @Test
    fun `generateGroupPolicy should fail if the member is not an mgm`() {
        mgmResourceClient.start()
        setUpRpcSender(
            MGMGroupPolicyResponse(
                "groupPolicy",
            )
        )
        val mgmContext = mock<MGMContext>()
        val memberInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn mgmContext
        }
        whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

        assertThrows<MemberNotAnMgmException> {
            mgmResourceClient.generateGroupPolicy(shortHash)
        }
        mgmResourceClient.stop()
    }

    @Nested
    inner class AddApprovalRuleTests {
        @Test
        fun `addApprovalRule should send the correct request`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            val params = ApprovalRuleParams(RULE_REGEX, ApprovalRuleType.STANDARD, RULE_LABEL)
            val operation = mock<MembershipPersistenceOperation<ApprovalRuleDetails>> {
                on { execute() } doReturn MembershipPersistenceResult.Success(ApprovalRuleDetails(RULE_ID, RULE_REGEX, RULE_LABEL))
            }
            whenever(
                membershipPersistenceClient.addApprovalRule(
                    holdingIdentity,
                    params
                )
            ).doReturn(
                operation
            )

            mgmResourceClient.addApprovalRule(
                shortHash,
                params
            )

            verify(membershipPersistenceClient).addApprovalRule(
                holdingIdentity,
                params
            )
            mgmResourceClient.stop()
        }

        @Test
        fun `addApprovalRule should fail if the member cannot be found`() {
            mgmResourceClient.start()
            setUpRpcSender(null)

            assertThrows<CouldNotFindEntityException> {
                mgmResourceClient.addApprovalRule(
                    ShortHash.of("000000000000"),
                    ApprovalRuleParams(RULE_REGEX, ApprovalRuleType.STANDARD, RULE_LABEL)
                )
            }
            mgmResourceClient.stop()
        }

        @Test
        fun `addApprovalRule should fail if the member cannot be read`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            whenever(groupReader.lookup(mgmX500Name)).doReturn(null)

            assertThrows<CouldNotFindEntityException> {
                mgmResourceClient.addApprovalRule(
                    shortHash,
                    ApprovalRuleParams(RULE_REGEX, ApprovalRuleType.STANDARD, RULE_LABEL)
                )
            }
            mgmResourceClient.stop()
        }

        @Test
        fun `addApprovalRule should fail if the member is not the MGM`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmResourceClient.addApprovalRule(
                    shortHash,
                    ApprovalRuleParams(RULE_REGEX, ApprovalRuleType.STANDARD, RULE_LABEL)
                )
            }
            mgmResourceClient.stop()
        }
    }

    @Nested
    inner class DeleteApprovalRuleTests {
        @Test
        fun `deleteApprovalRule should send the correct request`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            whenever(
                membershipPersistenceClient.deleteApprovalRule(
                    holdingIdentity,
                    RULE_ID,
                    RULE_TYPE
                )
            ).doReturn(
                unitOperation
            )

            mgmResourceClient.deleteApprovalRule(
                shortHash,
                RULE_ID,
                RULE_TYPE
            )

            verify(membershipPersistenceClient).deleteApprovalRule(
                holdingIdentity,
                RULE_ID,
                RULE_TYPE
            )
            mgmResourceClient.stop()
        }

        @Test
        fun `deleteApprovalRule should fail if the member cannot be found`() {
            mgmResourceClient.start()
            setUpRpcSender(null)

            assertThrows<CouldNotFindEntityException> {
                mgmResourceClient.deleteApprovalRule(
                    ShortHash.of("000000000000"),
                    RULE_ID,
                    RULE_TYPE
                )
            }
            mgmResourceClient.stop()
        }

        @Test
        fun `deleteApprovalRule should fail if the member cannot be read`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            whenever(groupReader.lookup(mgmX500Name)).doReturn(null)

            assertThrows<CouldNotFindEntityException> {
                mgmResourceClient.deleteApprovalRule(
                    shortHash,
                    RULE_ID,
                    RULE_TYPE
                )
            }
            mgmResourceClient.stop()
        }

        @Test
        fun `deleteApprovalRule should fail if the member is not the MGM`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmResourceClient.deleteApprovalRule(
                    shortHash,
                    RULE_ID,
                    RULE_TYPE
                )
            }
            mgmResourceClient.stop()
        }
    }

    @Nested
    inner class GetApprovalRulesTests {
        @Test
        fun `getApprovalRules should send the correct request`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            whenever(
                membershipQueryClient.getApprovalRules(
                    holdingIdentity,
                    ApprovalRuleType.STANDARD,
                )
            ).doReturn(
                MembershipQueryResult.Success(emptyList())
            )

            mgmResourceClient.getApprovalRules(
                shortHash,
                ApprovalRuleType.STANDARD,
            )

            verify(membershipQueryClient).getApprovalRules(
                holdingIdentity,
                ApprovalRuleType.STANDARD,
            )
            mgmResourceClient.stop()
        }

        @Test
        fun `getApprovalRules should fail if the member cannot be found`() {
            mgmResourceClient.start()
            setUpRpcSender(null)

            assertThrows<CouldNotFindEntityException> {
                mgmResourceClient.getApprovalRules(
                    ShortHash.of("000000000000"),
                    ApprovalRuleType.STANDARD
                )
            }
            mgmResourceClient.stop()
        }

        @Test
        fun `getApprovalRules should fail if the member cannot be read`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            whenever(groupReader.lookup(mgmX500Name)).doReturn(null)

            assertThrows<CouldNotFindEntityException> {
                mgmResourceClient.getApprovalRules(
                    shortHash,
                    ApprovalRuleType.STANDARD
                )
            }
            mgmResourceClient.stop()
        }

        @Test
        fun `getApprovalRules should fail if the member is not the MGM`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmResourceClient.getApprovalRules(
                    shortHash,
                    ApprovalRuleType.STANDARD
                )
            }
            mgmResourceClient.stop()
        }
    }

    @Nested
    inner class ViewRegistrationRequestsTests {
        @Test
        fun `viewRegistrationRequests should send the correct request`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            whenever(
                membershipQueryClient.queryRegistrationRequests(
                    holdingIdentity,
                    memberName,
                    listOf(RegistrationStatus.PENDING_MANUAL_APPROVAL)
                )
            ).doReturn(
                MembershipQueryResult.Success(emptyList())
            )

            mgmResourceClient.viewRegistrationRequests(
                shortHash,
                memberName,
                false
            )

            verify(membershipQueryClient).queryRegistrationRequests(
                holdingIdentity,
                memberName,
                listOf(RegistrationStatus.PENDING_MANUAL_APPROVAL)
            )
            mgmResourceClient.stop()
        }

        @Test
        fun `viewRegistrationRequests should fail if the member cannot be found`() {
            mgmResourceClient.start()
            setUpRpcSender(null)

            assertThrows<CouldNotFindEntityException> {
                mgmResourceClient.viewRegistrationRequests(
                    ShortHash.of("000000000000"),
                    memberName,
                    true
                )
            }
            mgmResourceClient.stop()
        }

        @Test
        fun `viewRegistrationRequests should fail if the member cannot be read`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            whenever(groupReader.lookup(mgmX500Name)).doReturn(null)

            assertThrows<CouldNotFindEntityException> {
                mgmResourceClient.viewRegistrationRequests(
                    shortHash,
                    memberName,
                    true
                )
            }
            mgmResourceClient.stop()
        }

        @Test
        fun `viewRegistrationRequests should fail if the member is not the MGM`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmResourceClient.viewRegistrationRequests(
                    shortHash,
                    memberName,
                    true
                )
            }
            mgmResourceClient.stop()
        }
    }

    @Nested
    inner class ReviewRegistrationRequestTests {
        @Test
        fun `reviewRegistrationRequest should publish the correct command when approved`() {
            whenever(coordinator.getManagedResource<Publisher>(any())).doReturn(publisher)
            whenever(publisher.publish(any())).doReturn(listOf(CompletableFuture.completedFuture(Unit)))
            val mockStatus = mock<RegistrationRequestDetails> {
                on { registrationStatus } doReturn RegistrationStatus.PENDING_MANUAL_APPROVAL
                on { memberProvidedContext } doReturn memberContext
            }
            whenever(membershipQueryClient.queryRegistrationRequest(any(), any())).doReturn(
                MembershipQueryResult.Success(mockStatus)
            )
            mgmResourceClient.start()
            setUpRpcSender(null)

            mgmResourceClient.reviewRegistrationRequest(
                shortHash,
                REQUEST_ID.uuid(),
                true
            )

            verify(publisher).publish(
                listOf(
                    Record(
                        Schemas.Membership.REGISTRATION_COMMAND_TOPIC,
                        "$memberName-$DEFAULT_MEMBER_GROUP_ID",
                        RegistrationCommand(ApproveRegistration())
                    )
                )
            )
            mgmResourceClient.stop()
        }

        @Test
        fun `reviewRegistrationRequest should publish the correct command when declined`() {
            whenever(coordinator.getManagedResource<Publisher>(any())).doReturn(publisher)
            whenever(publisher.publish(any())).doReturn(listOf(CompletableFuture.completedFuture(Unit)))
            val mockStatus = mock<RegistrationRequestDetails> {
                on { registrationStatus } doReturn RegistrationStatus.PENDING_MANUAL_APPROVAL
                on { memberProvidedContext } doReturn memberContext
            }
            whenever(membershipQueryClient.queryRegistrationRequest(any(), any())).doReturn(
                MembershipQueryResult.Success(mockStatus)
            )
            mgmResourceClient.start()
            setUpRpcSender(null)
            val reason = "sample reason"

            mgmResourceClient.reviewRegistrationRequest(
                shortHash,
                REQUEST_ID.uuid(),
                false,
                reason
            )

            verify(publisher).publish(
                listOf(
                    Record(
                        Schemas.Membership.REGISTRATION_COMMAND_TOPIC,
                        "$memberName-$DEFAULT_MEMBER_GROUP_ID",
                        RegistrationCommand(DeclineRegistration(reason, DECLINED_REASON_FOR_USER_GENERAL_MANUAL_DECLINED))
                    )
                )
            )
            mgmResourceClient.stop()
        }

        @Test
        fun `reviewRegistrationRequest should fail when context cannot be deserialized`() {
            whenever(coordinator.getManagedResource<Publisher>(any())).doReturn(publisher)
            whenever(publisher.publish(any())).doReturn(listOf(CompletableFuture.completedFuture(Unit)))
            val mockStatus = mock<RegistrationRequestDetails> {
                on { registrationStatus } doReturn RegistrationStatus.PENDING_MANUAL_APPROVAL
                on { memberProvidedContext } doReturn memberContext
            }
            whenever(membershipQueryClient.queryRegistrationRequest(any(), any())).doReturn(
                MembershipQueryResult.Success(mockStatus)
            )
            whenever(keyValuePairListDeserializer.deserialize(any())).thenReturn(null)
            mgmResourceClient.start()
            setUpRpcSender(null)

            assertThrows<ContextDeserializationException> {
                mgmResourceClient.reviewRegistrationRequest(
                    shortHash,
                    REQUEST_ID.uuid(),
                    true
                )
            }
            mgmResourceClient.stop()
        }

        @Test
        fun `reviewRegistrationRequest should fail when name cannot be retrieved`() {
            whenever(coordinator.getManagedResource<Publisher>(any())).doReturn(publisher)
            whenever(publisher.publish(any())).doReturn(listOf(CompletableFuture.completedFuture(Unit)))
            val mockStatus = mock<RegistrationRequestDetails> {
                on { registrationStatus } doReturn RegistrationStatus.PENDING_MANUAL_APPROVAL
                on { memberProvidedContext } doReturn memberContext
            }
            whenever(membershipQueryClient.queryRegistrationRequest(any(), any())).doReturn(
                MembershipQueryResult.Success(mockStatus)
            )
            whenever(keyValuePairListDeserializer.deserialize(any())).thenReturn(KeyValuePairList(emptyList()))
            mgmResourceClient.start()
            setUpRpcSender(null)

            assertThrows<IllegalArgumentException> {
                mgmResourceClient.reviewRegistrationRequest(
                    shortHash,
                    REQUEST_ID.uuid(),
                    true
                )
            }
            mgmResourceClient.stop()
        }

        @Test
        fun `reviewRegistrationRequest should fail if the member cannot be found`() {
            mgmResourceClient.start()
            setUpRpcSender(null)

            assertThrows<CouldNotFindEntityException> {
                mgmResourceClient.reviewRegistrationRequest(
                    ShortHash.of("000000000000"),
                    REQUEST_ID.uuid(),
                    true
                )
            }
            mgmResourceClient.stop()
        }

        @Test
        fun `reviewRegistrationRequest should fail if the member cannot be read`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            whenever(groupReader.lookup(mgmX500Name)).doReturn(null)

            assertThrows<CouldNotFindEntityException> {
                mgmResourceClient.reviewRegistrationRequest(
                    shortHash,
                    REQUEST_ID.uuid(),
                    true
                )
            }
            mgmResourceClient.stop()
        }

        @Test
        fun `reviewRegistrationRequest should fail if the member is not the MGM`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmResourceClient.reviewRegistrationRequest(
                    shortHash,
                    REQUEST_ID.uuid(),
                    true
                )
            }
            mgmResourceClient.stop()
        }

        @Test
        fun `reviewRegistrationRequest should fail if request is not pending review`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            val mockStatus = mock<RegistrationRequestDetails> {
                on { registrationStatus } doReturn RegistrationStatus.SENT_TO_MGM
            }
            whenever(membershipQueryClient.queryRegistrationRequest(any(), any())).doReturn(
                MembershipQueryResult.Success(mockStatus)
            )

            assertThrows<IllegalArgumentException> {
                mgmResourceClient.reviewRegistrationRequest(
                    shortHash,
                    REQUEST_ID.uuid(),
                    true
                )
            }
            mgmResourceClient.stop()
        }

        @Test
        fun `reviewRegistrationRequest should fail if request status cannot be determined`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            whenever(membershipQueryClient.queryRegistrationRequest(any(), any())).doReturn(
                MembershipQueryResult.Success(null)
            )

            assertThrows<IllegalArgumentException> {
                mgmResourceClient.reviewRegistrationRequest(
                    shortHash,
                    REQUEST_ID.uuid(),
                    true
                )
            }
            mgmResourceClient.stop()
        }
    }

    @Nested
    inner class ForceDeclineRegistrationRequestTests {
        @Test
        fun `forceDeclineRegistrationRequest should publish the correct command`() {
            whenever(coordinator.getManagedResource<Publisher>(any())).doReturn(publisher)
            whenever(publisher.publish(any())).doReturn(listOf(CompletableFuture.completedFuture(Unit)))
            val mockStatus = mock<RegistrationRequestDetails> {
                on { registrationStatus } doReturn RegistrationStatus.PENDING_MEMBER_VERIFICATION
                on { memberProvidedContext } doReturn memberContext
            }
            whenever(membershipQueryClient.queryRegistrationRequest(any(), any())).doReturn(
                MembershipQueryResult.Success(mockStatus)
            )
            mgmResourceClient.start()
            setUpRpcSender(null)

            mgmResourceClient.forceDeclineRegistrationRequest(
                shortHash,
                REQUEST_ID.uuid(),
            )

            verify(publisher).publish(
                listOf(
                    Record(
                        Schemas.Membership.REGISTRATION_COMMAND_TOPIC,
                        "$memberName-$DEFAULT_MEMBER_GROUP_ID",
                        RegistrationCommand(DeclineRegistration("Force declined by MGM", DECLINED_REASON_FOR_USER_GENERAL_MANUAL_DECLINED))
                    )
                )
            )
            mgmResourceClient.stop()
        }

        @Test
        fun `forceDeclineRegistrationRequest should fail when context cannot be deserialized`() {
            whenever(coordinator.getManagedResource<Publisher>(any())).doReturn(publisher)
            whenever(publisher.publish(any())).doReturn(listOf(CompletableFuture.completedFuture(Unit)))
            val mockStatus = mock<RegistrationRequestDetails> {
                on { registrationStatus } doReturn RegistrationStatus.PENDING_MEMBER_VERIFICATION
                on { memberProvidedContext } doReturn memberContext
            }
            whenever(membershipQueryClient.queryRegistrationRequest(any(), any())).doReturn(
                MembershipQueryResult.Success(mockStatus)
            )
            whenever(keyValuePairListDeserializer.deserialize(any())).thenReturn(null)
            mgmResourceClient.start()
            setUpRpcSender(null)

            assertThrows<ContextDeserializationException> {
                mgmResourceClient.forceDeclineRegistrationRequest(
                    shortHash,
                    REQUEST_ID.uuid(),
                )
            }
            verify(publisher, never()).publish(any())
            mgmResourceClient.stop()
        }

        @Test
        fun `forceDeclineRegistrationRequest should fail when name cannot be retrieved`() {
            whenever(coordinator.getManagedResource<Publisher>(any())).doReturn(publisher)
            whenever(publisher.publish(any())).doReturn(listOf(CompletableFuture.completedFuture(Unit)))
            val mockStatus = mock<RegistrationRequestDetails> {
                on { registrationStatus } doReturn RegistrationStatus.PENDING_MEMBER_VERIFICATION
                on { memberProvidedContext } doReturn memberContext
            }
            whenever(membershipQueryClient.queryRegistrationRequest(any(), any())).doReturn(
                MembershipQueryResult.Success(mockStatus)
            )
            whenever(keyValuePairListDeserializer.deserialize(any())).thenReturn(KeyValuePairList(emptyList()))
            mgmResourceClient.start()
            setUpRpcSender(null)

            assertThrows<IllegalArgumentException> {
                mgmResourceClient.forceDeclineRegistrationRequest(
                    shortHash,
                    REQUEST_ID.uuid(),
                )
            }
            verify(publisher, never()).publish(any())
            mgmResourceClient.stop()
        }

        @Test
        fun `forceDeclineRegistrationRequest should fail for completed requests`() {
            val mockStatus = mock<RegistrationRequestDetails> {
                on { registrationStatus } doReturn RegistrationStatus.APPROVED
                on { memberProvidedContext } doReturn memberContext
            }
            whenever(membershipQueryClient.queryRegistrationRequest(any(), any())).doReturn(
                MembershipQueryResult.Success(mockStatus)
            )
            mgmResourceClient.start()
            setUpRpcSender(null)

            assertThrows<IllegalArgumentException> {
                mgmResourceClient.forceDeclineRegistrationRequest(
                    shortHash,
                    REQUEST_ID.uuid(),
                )
            }
            verify(publisher, never()).publish(any())
            mgmResourceClient.stop()
        }

        @Test
        fun `forceDeclineRegistrationRequest should fail if the MGM cannot be found`() {
            mgmResourceClient.start()
            setUpRpcSender(null)

            assertThrows<CouldNotFindEntityException> {
                mgmResourceClient.forceDeclineRegistrationRequest(
                    ShortHash.of("000000000000"),
                    REQUEST_ID.uuid(),
                )
            }
            mgmResourceClient.stop()
        }

        @Test
        fun `forceDeclineRegistrationRequest should fail if the MGM cannot be read`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            whenever(groupReader.lookup(mgmX500Name)).doReturn(null)

            assertThrows<CouldNotFindEntityException> {
                mgmResourceClient.forceDeclineRegistrationRequest(
                    shortHash,
                    REQUEST_ID.uuid(),
                )
            }
            mgmResourceClient.stop()
        }

        @Test
        fun `forceDeclineRegistrationRequest should fail if holding identity ID does not belong to the MGM`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmResourceClient.forceDeclineRegistrationRequest(
                    shortHash,
                    REQUEST_ID.uuid(),
                )
            }
            mgmResourceClient.stop()
        }

        @Test
        fun `forceDeclineRegistrationRequest should fail if request status cannot be determined`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            whenever(membershipQueryClient.queryRegistrationRequest(any(), any())).doReturn(
                MembershipQueryResult.Success(null)
            )

            assertThrows<IllegalArgumentException> {
                mgmResourceClient.forceDeclineRegistrationRequest(
                    shortHash,
                    REQUEST_ID.uuid(),
                )
            }
            mgmResourceClient.stop()
        }
    }

    @Nested
    inner class LifecycleTests {
        @Test
        fun `starting and stopping the service succeeds`() {
            mgmResourceClient.start()
            assertTrue(mgmResourceClient.isRunning)
            mgmResourceClient.stop()
            assertFalse(mgmResourceClient.isRunning)
        }

        @Test
        fun `start event starts following the statuses of the required dependencies`() {
            startComponent()

            verify(coordinator).followStatusChangesByName(
                eq(
                    setOf(
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                        LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
                        LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
                        LifecycleCoordinatorName.forComponent<MembershipPersistenceClient>(),
                        LifecycleCoordinatorName.forComponent<MembershipQueryClient>(),
                    )
                )
            )
        }

        @Test
        fun `stop event will close managed resources and set status to down`() {
            stopComponent()

            verify(coordinator).closeManagedResources(
                argThat {
                    size == 3
                }
            )
            verify(coordinator).updateStatus(LifecycleStatus.DOWN, "Handling the stop event for component.")
        }

        @Test
        fun `start will start the coordinator`() {
            mgmResourceClient.start()

            verify(coordinator).start()
        }

        @Test
        fun `stop will stop the coordinator`() {
            mgmResourceClient.stop()

            verify(coordinator).stop()
        }

        @Test
        fun `config changed event will create the publisher`() {
            changeConfig()

            verify(publisherFactory).createPublisher(any(), eq(messagingConfig))
        }

        @Test
        fun `config changed event will start the publisher`() {
            changeConfig()

            verify(publisher).start()
        }

        @Test
        fun `registration status UP registers for config updates`() {
            changeRegistrationStatus(LifecycleStatus.UP)

            verify(configurationReadService).registerComponentForUpdates(
                any(),
                any()
            )
            verify(coordinator, never()).updateStatus(eq(LifecycleStatus.UP), any())
        }

        @Test
        fun `registration status DOWN sets component status to DOWN and closes resource`() {
            startComponent()
            changeRegistrationStatus(LifecycleStatus.UP)
            changeRegistrationStatus(LifecycleStatus.DOWN)

            verify(coordinator).closeManagedResources(argThat { size == 1 })
            verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        }

        @Test
        fun `registration status ERROR sets component status to DOWN`() {
            startComponent()
            changeRegistrationStatus(LifecycleStatus.UP)
            changeRegistrationStatus(LifecycleStatus.ERROR)

            verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        }

        @Test
        fun `registration status DOWN closes config handle if status was previously UP`() {
            startComponent()
            changeRegistrationStatus(LifecycleStatus.UP)

            verify(configurationReadService).registerComponentForUpdates(
                any(),
                any()
            )

            changeRegistrationStatus(LifecycleStatus.DOWN)

            verify(coordinator).closeManagedResources(argThat { size == 1 })
        }

        @Test
        fun `after receiving the messaging configuration the rpc sender is initialized`() {
            changeConfig()
            verify(publisherFactory).createRPCSender(
                any<RPCConfig<MembershipRpcRequest, MembershipRpcResponse>>(),
                any()
            )
            verify(rpcSender).start()
            verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())
        }
    }

    @Nested
    inner class MutualTlsAllowClientCertificateTests {
        @Test
        fun `it should fail when client is not ready`() {
            mgmResourceClient.start()

            assertThrows<IllegalStateException> {
                mgmResourceClient.mutualTlsAllowClientCertificate(
                    shortHash,
                    mgmX500Name,
                )
            }
        }

        @Test
        fun `it should fail when not an MGM`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmResourceClient.mutualTlsAllowClientCertificate(
                    shortHash,
                    mgmX500Name,
                )
            }
        }

        @Test
        fun `it should send the correct request`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            whenever(
                membershipPersistenceClient.mutualTlsAddCertificateToAllowedList(
                    holdingIdentity,
                    mgmX500Name.toString(),
                )
            ).doReturn(
                unitOperation
            )

            mgmResourceClient.mutualTlsAllowClientCertificate(
                shortHash,
                mgmX500Name,
            )

            verify(membershipPersistenceClient).mutualTlsAddCertificateToAllowedList(
                holdingIdentity,
                mgmX500Name.toString(),
            )
        }
    }

    @Nested
    inner class MutualTlsDisallowClientCertificateTest {
        @Test
        fun `it should fail when client is not ready`() {
            mgmResourceClient.start()

            assertThrows<IllegalStateException> {
                mgmResourceClient.mutualTlsDisallowClientCertificate(
                    shortHash,
                    mgmX500Name,
                )
            }
        }

        @Test
        fun `it should fail when not an MGM`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmResourceClient.mutualTlsDisallowClientCertificate(
                    shortHash,
                    mgmX500Name,
                )
            }
        }

        @Test
        fun `it should send the correct request`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            whenever(
                membershipPersistenceClient.mutualTlsRemoveCertificateFromAllowedList(
                    holdingIdentity,
                    mgmX500Name.toString(),
                )
            ).doReturn(
                unitOperation
            )

            mgmResourceClient.mutualTlsDisallowClientCertificate(
                shortHash,
                mgmX500Name,
            )

            verify(membershipPersistenceClient).mutualTlsRemoveCertificateFromAllowedList(
                holdingIdentity,
                mgmX500Name.toString(),
            )
        }
    }

    @Nested
    inner class MutualTlsListClientCertificateTest {
        @Test
        fun `it should fail when client is not ready`() {
            mgmResourceClient.start()

            assertThrows<IllegalStateException> {
                mgmResourceClient.mutualTlsListClientCertificate(
                    shortHash,
                )
            }
        }

        @Test
        fun `it should fail when not an MGM`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmResourceClient.mutualTlsListClientCertificate(
                    shortHash,
                )
            }
        }

        @Test
        fun `it return the correct value`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            whenever(
                membershipQueryClient.mutualTlsListAllowedCertificates(holdingIdentity)
            ).doReturn(
                MembershipQueryResult.Success(
                    listOf(mgmX500Name.toString())
                )
            )

            val subjects = mgmResourceClient.mutualTlsListClientCertificate(
                shortHash,
            )

            assertThat(subjects).containsExactly(
                mgmX500Name
            )
        }
    }

    @Nested
    inner class GeneratePreAuthTokenTest {
        @Test
        fun `it should fail when client is not ready`() {
            mgmResourceClient.start()

            assertThrows<IllegalStateException> {
                mgmResourceClient.generatePreAuthToken(shortHash, mgmX500Name, null, null)
            }
        }

        @Test
        fun `it should fail when not an MGM`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmResourceClient.generatePreAuthToken(shortHash, mgmX500Name, null, null)
            }
        }

        @Test
        fun `it return the correct value`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            val ttl = Instant.ofEpochSecond(100)
            val ownerX500Name = MemberX500Name.parse("CN=Bob,OU=Unit1,O=Alice,L=London,ST=State1,C=GB")
            val remark = "A remark"
            val uuidCaptor = argumentCaptor<UUID>()
            whenever(
                membershipPersistenceClient.generatePreAuthToken(
                    eq(holdingIdentity),
                    uuidCaptor.capture(),
                    eq(ownerX500Name),
                    eq(ttl),
                    eq(remark)
                )
            ).doReturn(
                unitOperation
            )

            val token = mgmResourceClient.generatePreAuthToken(shortHash, ownerX500Name, ttl, remark)

            assertThat(token).isEqualTo(
                PreAuthToken(uuidCaptor.firstValue.toString(), ownerX500Name.toString(), ttl, PreAuthTokenStatus.AVAILABLE, remark, null)
            )
        }
    }

    @Nested
    inner class GetPreAuthTokensTest {
        @Test
        fun `it should fail when client is not ready`() {
            mgmResourceClient.start()

            assertThrows<IllegalStateException> {
                mgmResourceClient.getPreAuthTokens(shortHash, null, null, false)
            }
        }

        @Test
        fun `it should fail when not an MGM`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmResourceClient.getPreAuthTokens(shortHash, null, null, false)
            }
        }

        @Test
        fun `it return the correct value`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            val ownerX500Name = MemberX500Name.parse("CN=Bob,OU=Unit1,O=Alice,L=London,ST=State1,C=GB")
            val tokenId = UUID.randomUUID()
            val viewInactive = false
            val mockToken1 = mock<PreAuthToken>()
            val mockToken2 = mock<PreAuthToken>()
            whenever(
                membershipQueryClient.queryPreAuthTokens(holdingIdentity, ownerX500Name, tokenId, viewInactive)
            ).doReturn(
                MembershipQueryResult.Success(listOf(mockToken1, mockToken2))
            )

            val queryResult = mgmResourceClient.getPreAuthTokens(shortHash, ownerX500Name, tokenId, viewInactive)

            assertThat(queryResult).containsExactly(mockToken1, mockToken2)
        }
    }

    @Nested
    inner class RevokePreAuthToken {
        private val tokenId = UUID.randomUUID()

        @Test
        fun `it should fail when client is not ready`() {
            mgmResourceClient.start()

            assertThrows<IllegalStateException> {
                mgmResourceClient.revokePreAuthToken(shortHash, tokenId, null)
            }
        }

        @Test
        fun `it should fail when not an MGM`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmResourceClient.revokePreAuthToken(shortHash, tokenId, null)
            }
        }

        @Test
        fun `it return the correct value`() {
            mgmResourceClient.start()
            setUpRpcSender(null)
            val ownerX500Name = MemberX500Name.parse("CN=Bob,OU=Unit1,O=Alice,L=London,ST=State1,C=GB")
            val ttl = Instant.ofEpochSecond(100)
            val token = PreAuthToken(tokenId.toString(), ownerX500Name.toString(), ttl, PreAuthTokenStatus.REVOKED, "", "")
            val operation = mock<MembershipPersistenceOperation<PreAuthToken>> {
                on { getOrThrow() } doReturn token
            }
            whenever(
                membershipPersistenceClient.revokePreAuthToken(holdingIdentity, tokenId, null)
            ).doReturn(
                operation
            )

            val revokedToken = mgmResourceClient.revokePreAuthToken(shortHash, tokenId, null)

            assertThat(revokedToken).isEqualTo(token)
        }
    }

    @Nested
    inner class SuspendMemberTests {
        @BeforeEach
        fun setUp() = mgmResourceClient.start()

        @AfterEach
        fun tearDown() = mgmResourceClient.stop()

        @Test
        fun `suspendMember should send the correct request`() {
            setUpRpcSender(null)
            whenever(
                membershipPersistenceClient.suspendMember(
                    holdingIdentity,
                    memberName,
                    SERIAL,
                    REASON
                )
            ).doReturn(
                Operation(MembershipPersistenceResult.Success(mock<PersistentMemberInfo>() to null))
            )

            mgmResourceClient.suspendMember(
                shortHash,
                memberName,
                SERIAL,
                REASON
            )

            verify(membershipPersistenceClient).suspendMember(
                holdingIdentity,
                memberName,
                SERIAL,
                REASON
            )
        }

        @Test
        fun `suspendMember should publish the updated member info and request distribution`() {
            whenever(coordinator.getManagedResource<Publisher>(any())).doReturn(publisher)
            whenever(publisher.publish(any())).doReturn(listOf(CompletableFuture.completedFuture(Unit)))
            val mockMemberInfo = mock<PersistentMemberInfo>()
            whenever(membershipPersistenceClient.suspendMember(eq(holdingIdentity), eq(memberName), any(), any()))
                .doReturn(Operation(MembershipPersistenceResult.Success(mockMemberInfo to null)))
            mgmResourceClient.start()
            setUpRpcSender(null)

            mgmResourceClient.suspendMember(shortHash, memberName, SERIAL, REASON)

            verify(publisher).publish(
                listOf(
                    Record(
                        Schemas.Membership.MEMBER_LIST_TOPIC,
                        "$shortHash-${bob.id}",
                        mockMemberInfo
                    ),
                    Record(
                        topic = Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC,
                        key = "$memberName-${DEFAULT_MEMBER_GROUP_ID}",
                        value = MembershipActionsRequest(
                            DistributeMemberInfo(
                                HoldingIdentity(mgmX500Name, DEFAULT_MEMBER_GROUP_ID).toAvro(),
                                HoldingIdentity(memberName, DEFAULT_MEMBER_GROUP_ID).toAvro(),
                                null,
                                0
                            )
                        )
                    )
                )
            )
            mgmResourceClient.stop()
        }

        @Test
        fun `suspendMember should publish the updated group parameters, the updated member info and request distribution`() {
            val groupParametersEpoch = 5
            whenever(coordinator.getManagedResource<Publisher>(any())).doReturn(publisher)
            val publisherCaptor = argumentCaptor<List<Record<String, Any>>>()
            whenever(publisher.publish(publisherCaptor.capture())).doReturn(listOf(CompletableFuture.completedFuture(Unit)))
            val mockMemberInfo = mock<PersistentMemberInfo>()
            val serializedGroupParameters = "group-params".toByteArray()
            val groupParameters = mock<InternalGroupParameters> {
                on { groupParameters } doReturn serializedGroupParameters
                on { epoch } doReturn groupParametersEpoch
            }
            whenever(membershipPersistenceClient.suspendMember(eq(holdingIdentity), eq(memberName), any(), any()))
                .doReturn(Operation(MembershipPersistenceResult.Success(mockMemberInfo to groupParameters)))
            mgmResourceClient.start()
            setUpRpcSender(null)

            mgmResourceClient.suspendMember(shortHash, memberName, SERIAL, REASON)

            val publishedParams = publisherCaptor.allValues.single().single { it.topic == Schemas.Membership.GROUP_PARAMETERS_TOPIC }.value
                as PersistentGroupParameters
            assertThat(publishedParams.viewOwner).isEqualTo(holdingIdentity.toAvro())

            assertThat(publishedParams.groupParameters.groupParameters)
                .isEqualTo(ByteBuffer.wrap(serializedGroupParameters))

            assertThat(publishedParams.groupParameters.mgmSignature).isNull()
            assertThat(publishedParams.groupParameters.mgmSignatureSpec).isNull()

            val otherRecords = publisherCaptor.allValues.single().filterNot { it.topic == Schemas.Membership.GROUP_PARAMETERS_TOPIC }
            assertThat(otherRecords).containsExactlyInAnyOrderElementsOf(
                listOf(
                    Record(
                        Schemas.Membership.MEMBER_LIST_TOPIC,
                        "$shortHash-${bob.id}",
                        mockMemberInfo
                    ),
                    Record(
                        topic = Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC,
                        key = "$memberName-${DEFAULT_MEMBER_GROUP_ID}",
                        value = MembershipActionsRequest(
                            DistributeMemberInfo(
                                HoldingIdentity(mgmX500Name, DEFAULT_MEMBER_GROUP_ID).toAvro(),
                                HoldingIdentity(memberName, DEFAULT_MEMBER_GROUP_ID).toAvro(),
                                groupParametersEpoch,
                                0
                            )
                        )
                    )
                )
            )

            mgmResourceClient.stop()
        }

        @Test
        fun `suspendMember should ignore failure to publish`() {
            whenever(coordinator.getManagedResource<Publisher>(any())).doReturn(publisher)
            val mockFuture = mock<CompletableFuture<Unit>> {
                on { getOrThrow() } doThrow CordaMessageAPIIntermittentException("fail")
            }
            whenever(publisher.publish(any())).doReturn(listOf(mockFuture))
            val mockMemberInfo = mock<PersistentMemberInfo>()
            whenever(membershipPersistenceClient.suspendMember(eq(holdingIdentity), eq(memberName), any(), any()))
                .doReturn(Operation(MembershipPersistenceResult.Success(mockMemberInfo to null)))
            mgmResourceClient.start()
            setUpRpcSender(null)

            assertDoesNotThrow { mgmResourceClient.suspendMember(shortHash, memberName, SERIAL, REASON) }

            mgmResourceClient.stop()
        }

        @Test
        fun `suspendMember should fail if the member cannot be found`() {
            setUpRpcSender(null)

            assertThrows<CouldNotFindEntityException> {
                mgmResourceClient.suspendMember(
                    ShortHash.of("000000000000"),
                    memberName
                )
            }
        }

        @Test
        fun `suspendMember should fail if the member cannot be read`() {
            setUpRpcSender(null)
            whenever(groupReader.lookup(mgmX500Name)).doReturn(null)

            assertThrows<CouldNotFindEntityException> {
                mgmResourceClient.suspendMember(shortHash, memberName)
            }
        }

        @Test
        fun `suspendMember should fail if the member is not the MGM`() {
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmResourceClient.suspendMember(shortHash, memberName)
            }
        }

        @Test
        fun `suspendMember should fail if the member to be suspended is the MGM`() {
            setUpRpcSender(null)

            assertThrows<IllegalArgumentException> {
                mgmResourceClient.suspendMember(shortHash, mgmX500Name)
            }
        }

        @Test
        fun `suspendMember should fail if the member to be suspended is not found`() {
            setUpRpcSender(null)
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(null)

            assertThrows<NoSuchElementException> {
                mgmResourceClient.suspendMember(shortHash, memberName)
            }
        }
    }

    @Nested
    inner class ActivateMemberTests {
        @BeforeEach
        fun setUp() = mgmResourceClient.start()

        @AfterEach
        fun tearDown() = mgmResourceClient.stop()

        @Test
        fun `activateMember should send the correct request`() {
            setUpRpcSender(null)
            whenever(
                membershipPersistenceClient.activateMember(
                    holdingIdentity,
                    memberName,
                    SERIAL,
                    REASON
                )
            ).doReturn(
                Operation(MembershipPersistenceResult.Success(mock<PersistentMemberInfo>() to null))
            )

            mgmResourceClient.activateMember(
                shortHash,
                memberName,
                SERIAL,
                REASON
            )

            verify(membershipPersistenceClient).activateMember(
                holdingIdentity,
                memberName,
                SERIAL,
                REASON
            )
        }

        @Test
        fun `activateMember should publish the updated member info`() {
            whenever(coordinator.getManagedResource<Publisher>(any())).doReturn(publisher)
            whenever(publisher.publish(any())).doReturn(listOf(CompletableFuture.completedFuture(Unit)))
            val mockMemberInfo = mock<PersistentMemberInfo>()
            whenever(membershipPersistenceClient.activateMember(eq(holdingIdentity), eq(memberName), any(), any()))
                .doReturn(Operation(MembershipPersistenceResult.Success(mockMemberInfo to null)))
            mgmResourceClient.start()
            setUpRpcSender(null)

            mgmResourceClient.activateMember(shortHash, memberName, SERIAL, REASON)

            verify(publisher).publish(
                listOf(
                    Record(
                        Schemas.Membership.MEMBER_LIST_TOPIC,
                        "$shortHash-${bob.id}",
                        mockMemberInfo
                    ),
                    Record(
                        topic = Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC,
                        key = "$memberName-${DEFAULT_MEMBER_GROUP_ID}",
                        value = MembershipActionsRequest(
                            DistributeMemberInfo(
                                HoldingIdentity(mgmX500Name, DEFAULT_MEMBER_GROUP_ID).toAvro(),
                                HoldingIdentity(memberName, DEFAULT_MEMBER_GROUP_ID).toAvro(),
                                null,
                                0
                            )
                        )
                    )
                )
            )
            mgmResourceClient.stop()
        }

        @Test
        fun `activateMember should publish the updated group parameters, the updated member info and request distribution`() {
            val groupParametersEpoch = 5
            whenever(coordinator.getManagedResource<Publisher>(any())).doReturn(publisher)
            val publisherCaptor = argumentCaptor<List<Record<String, Any>>>()
            whenever(publisher.publish(publisherCaptor.capture())).doReturn(listOf(CompletableFuture.completedFuture(Unit)))
            val mockMemberInfo = mock<PersistentMemberInfo>()
            val serializedGroupParameters = "group-params".toByteArray()
            val groupParameters = mock<InternalGroupParameters> {
                on { groupParameters } doReturn serializedGroupParameters
                on { epoch } doReturn groupParametersEpoch
            }
            whenever(membershipPersistenceClient.activateMember(eq(holdingIdentity), eq(memberName), any(), any()))
                .doReturn(Operation(MembershipPersistenceResult.Success(mockMemberInfo to groupParameters)))
            mgmResourceClient.start()
            setUpRpcSender(null)

            mgmResourceClient.activateMember(shortHash, memberName, SERIAL, REASON)

            val publishedParams = publisherCaptor.allValues.single().single { it.topic == Schemas.Membership.GROUP_PARAMETERS_TOPIC }.value
                as PersistentGroupParameters
            assertThat(publishedParams.viewOwner).isEqualTo(holdingIdentity.toAvro())

            assertThat(publishedParams.groupParameters.groupParameters)
                .isEqualTo(ByteBuffer.wrap(serializedGroupParameters))

            assertThat(publishedParams.groupParameters.mgmSignature).isNull()
            assertThat(publishedParams.groupParameters.mgmSignatureSpec).isNull()

            val otherRecords = publisherCaptor.allValues.single().filterNot { it.topic == Schemas.Membership.GROUP_PARAMETERS_TOPIC }
            assertThat(otherRecords).containsExactlyInAnyOrderElementsOf(
                listOf(
                    Record(
                        Schemas.Membership.MEMBER_LIST_TOPIC,
                        "$shortHash-${bob.id}",
                        mockMemberInfo
                    ),
                    Record(
                        topic = Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC,
                        key = "$memberName-${DEFAULT_MEMBER_GROUP_ID}",
                        value = MembershipActionsRequest(
                            DistributeMemberInfo(
                                HoldingIdentity(mgmX500Name, DEFAULT_MEMBER_GROUP_ID).toAvro(),
                                HoldingIdentity(memberName, DEFAULT_MEMBER_GROUP_ID).toAvro(),
                                groupParametersEpoch,
                                0
                            )
                        )
                    )
                )
            )

            mgmResourceClient.stop()
        }

        @Test
        fun `activateMember should ignore failure to publish`() {
            whenever(coordinator.getManagedResource<Publisher>(any())).doReturn(publisher)
            val mockFuture = mock<CompletableFuture<Unit>> {
                on { getOrThrow() } doThrow CordaMessageAPIIntermittentException("fail")
            }
            whenever(publisher.publish(any())).doReturn(listOf(mockFuture))
            val mockMemberInfo = mock<PersistentMemberInfo>()
            whenever(membershipPersistenceClient.activateMember(eq(holdingIdentity), eq(memberName), any(), any()))
                .doReturn(Operation(MembershipPersistenceResult.Success(mockMemberInfo to null)))
            mgmResourceClient.start()
            setUpRpcSender(null)

            assertDoesNotThrow { mgmResourceClient.activateMember(shortHash, memberName, SERIAL, REASON) }

            mgmResourceClient.stop()
        }

        @Test
        fun `activateMember should fail if the member cannot be found`() {
            setUpRpcSender(null)

            assertThrows<CouldNotFindEntityException> {
                mgmResourceClient.activateMember(
                    ShortHash.of("000000000000"),
                    memberName
                )
            }
        }

        @Test
        fun `activateMember should fail if the member cannot be read`() {
            setUpRpcSender(null)
            whenever(groupReader.lookup(mgmX500Name)).doReturn(null)

            assertThrows<CouldNotFindEntityException> {
                mgmResourceClient.activateMember(shortHash, memberName)
            }
        }

        @Test
        fun `activateMember should fail if the member is not the MGM`() {
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmResourceClient.activateMember(shortHash, memberName)
            }
        }

        @Test
        fun `activateMember should fail if the member to be activated is the MGM`() {
            setUpRpcSender(null)

            assertThrows<IllegalArgumentException> {
                mgmResourceClient.activateMember(shortHash, mgmX500Name)
            }
        }

        @Test
        fun `activateMember should fail if the member to be activated is not found`() {
            setUpRpcSender(null)
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(null)

            assertThrows<NoSuchElementException> {
                mgmResourceClient.activateMember(shortHash, memberName)
            }
        }
    }

    @Nested
    inner class UpdateGroupParametersTests {
        @BeforeEach
        fun setUp() = mgmResourceClient.start()

        @AfterEach
        fun tearDown() = mgmResourceClient.stop()

        private val mockUpdate = mock<Map<String, String>>()

        @Test
        fun `updateGroupParameters should send the correct request`() {
            setUpRpcSender(null)
            whenever(
                membershipPersistenceClient.updateGroupParameters(
                    holdingIdentity,
                    mockUpdate
                )
            ).doReturn(
                Operation(MembershipPersistenceResult.Success(mock()))
            )

            mgmResourceClient.updateGroupParameters(
                shortHash,
                mockUpdate
            )

            verify(membershipPersistenceClient).updateGroupParameters(
                holdingIdentity,
                mockUpdate
            )
        }

        @Test
        fun `updateGroupParameters should publish the distribution request`() {
            val groupParametersEpoch = 5
            whenever(coordinator.getManagedResource<Publisher>(any())).doReturn(publisher)
            whenever(publisher.publish(any())).doReturn(listOf(CompletableFuture.completedFuture(Unit)))
            val mockParameters = mock<InternalGroupParameters> {
                on { epoch } doReturn groupParametersEpoch
            }
            whenever(membershipPersistenceClient.updateGroupParameters(eq(holdingIdentity), any()))
                .doReturn(Operation(MembershipPersistenceResult.Success(mockParameters)))
            mgmResourceClient.start()
            setUpRpcSender(null)

            mgmResourceClient.updateGroupParameters(shortHash, mockUpdate)

            verify(publisher).publish(
                listOf(
                    Record(
                        topic = Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC,
                        key = "$mgmX500Name-${DEFAULT_MEMBER_GROUP_ID}",
                        value = MembershipActionsRequest(
                            DistributeGroupParameters(
                                HoldingIdentity(mgmX500Name, DEFAULT_MEMBER_GROUP_ID).toAvro(),
                                groupParametersEpoch
                            )
                        )
                    )
                )
            )
            mgmResourceClient.stop()
        }

        @Test
        fun `updateGroupParameters with no changes should return current group parameters`() {
            whenever(coordinator.getManagedResource<Publisher>(any())).doReturn(publisher)
            whenever(membershipPersistenceClient.updateGroupParameters(eq(holdingIdentity), any()))
                .doReturn(Operation(MembershipPersistenceResult.Success(mock())))
            val mockParameters = mock<InternalGroupParameters> {
                on { entries } doReturn mapOf(EPOCH_KEY to "1").entries
            }
            whenever(groupReader.groupParameters).doReturn(mockParameters)
            mgmResourceClient.start()
            setUpRpcSender(null)

            val result = mgmResourceClient.updateGroupParameters(shortHash, emptyMap())

            assertThat(result).isEqualTo(mockParameters)
            verify(membershipPersistenceClient, never()).updateGroupParameters(any(), any())
            verify(publisher, never()).publish(any())

            mgmResourceClient.stop()
        }

        @Test
        fun `updateGroupParameters should fail if the member cannot be found`() {
            setUpRpcSender(null)

            assertThrows<CouldNotFindEntityException> {
                mgmResourceClient.updateGroupParameters(
                    ShortHash.of("000000000000"),
                    mockUpdate
                )
            }
        }

        @Test
        fun `updateGroupParameters should fail if the member cannot be read`() {
            setUpRpcSender(null)
            whenever(groupReader.lookup(mgmX500Name)).doReturn(null)

            assertThrows<CouldNotFindEntityException> {
                mgmResourceClient.updateGroupParameters(shortHash, mockUpdate)
            }
        }

        @Test
        fun `updateGroupParameters should fail if the member is not the MGM`() {
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmResourceClient.updateGroupParameters(shortHash, mockUpdate)
            }
        }
    }
}
