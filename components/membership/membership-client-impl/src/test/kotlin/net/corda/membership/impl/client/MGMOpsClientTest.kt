package net.corda.membership.impl.client

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.impl.converter.PublicKeyConverter
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationStatus
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
import net.corda.membership.client.CouldNotFindMemberException
import net.corda.membership.client.MemberNotAnMgmException
import net.corda.membership.lib.EndpointInfoFactory
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.approval.ApprovalRuleParams
import net.corda.membership.lib.impl.MemberInfoFactoryImpl
import net.corda.membership.lib.impl.converter.EndpointInfoConverter
import net.corda.membership.lib.impl.converter.MemberNotaryDetailsConverter
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
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
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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
import java.security.PublicKey
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MGMOpsClientTest {
    private companion object {
        const val HOLDING_IDENTITY_STRING = "1234567890AB"
        const val KNOWN_KEY = "12345"
        const val RULE_REGEX = "rule-regex"
        const val RULE_LABEL = "rule-label"
        const val RULE_ID = "rule-id"
        const val REQUEST_ID = "b305129b-8c92-4092-b3a2-e6d452ce2b01"

        val RULE_TYPE = ApprovalRuleType.STANDARD
        val memberName = MemberX500Name.parse("CN=Member,O=Alice,OU=Unit1,L=London,ST=State1,C=GB")
        val mgmX500Name = MemberX500Name.parse("CN=Alice,OU=Unit1,O=Alice,L=London,ST=State1,C=GB")
        val holdingIdentity = createTestHoldingIdentity(mgmX500Name.toString(), "DEFAULT_MEMBER_GROUP_ID")
        val shortHash = ShortHash.of(HOLDING_IDENTITY_STRING)
        val clock = TestClock(Instant.ofEpochSecond(100))

        fun String.uuid(): UUID = UUID.fromString(this)
    }

    private val virtualNodeInfoReadService: VirtualNodeInfoReadService= mock {
        on { getByHoldingIdentityShortHash(shortHash) } doReturn VirtualNodeInfo(
            holdingIdentity,
            CpiIdentifier("test", "test", SecureHash("algorithm", "1234".toByteArray())),
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
            MemberInfoExtension.PLATFORM_VERSION to "5000",
        ),
        sortedMapOf(
            MemberInfoExtension.STATUS to MemberInfoExtension.MEMBER_STATUS_ACTIVE,
            MemberInfoExtension.MODIFIED_TIME to clock.instant().toString(),
            IS_MGM to "true",
            MemberInfoExtension.SERIAL to "1",
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
            Unit
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
    private val mgmOpsClient = MGMOpsClientImpl(
        lifecycleCoordinatorFactory,
        publisherFactory,
        configurationReadService,
        membershipGroupReaderProvider,
        virtualNodeInfoReadService,
        membershipPersistenceClient,
        membershipQueryClient,
    )

    private val messagingConfig: SmartConfig = mock()
    private val bootConfig: SmartConfig = mock ()

    private val configs = mapOf(
        ConfigKeys.BOOT_CONFIG to bootConfig,
        ConfigKeys.MESSAGING_CONFIG to messagingConfig
    )

    private fun startComponent() = lifecycleHandler?.processEvent(StartEvent(), coordinator)
    private fun stopComponent() = lifecycleHandler?.processEvent(StopEvent(), coordinator)
    private fun changeRegistrationStatus(status: LifecycleStatus) = lifecycleHandler?.processEvent(
        RegistrationStatusChangeEvent(mock(), status), coordinator
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
        mgmOpsClient.start()
        setUpRpcSender(
            MGMGroupPolicyResponse(
                "groupPolicy",
            )
        )
        mgmOpsClient.generateGroupPolicy(shortHash)
        mgmOpsClient.stop()

        val requestSent = rpcRequest.firstValue.request as? MGMGroupPolicyRequest

        assertThat(requestSent?.holdingIdentityId).isEqualTo(HOLDING_IDENTITY_STRING)
    }

    @Test
    fun `should fail when rpc sender is not ready`() {
        mgmOpsClient.start()
        val ex = assertFailsWith<IllegalStateException> {
            mgmOpsClient.generateGroupPolicy(shortHash)
        }
        assertTrue { ex.message!!.contains("incorrect state") }
        mgmOpsClient.stop()
    }

    @Test
    fun `should fail when service is not running`() {
        val ex = assertFailsWith<IllegalStateException> {
            mgmOpsClient.generateGroupPolicy(shortHash)
        }
        assertTrue { ex.message!!.contains("incorrect state") }
    }

    @Test
    fun `should fail when there is an RPC sender exception while sending the request`() {
        mgmOpsClient.start()
        changeConfig()
        val message = "Sender exception."
        whenever(rpcSender.sendRequest(any())).doThrow(CordaRPCAPISenderException(message))
        val ex = assertFailsWith<CordaRuntimeException> {
            mgmOpsClient.generateGroupPolicy(shortHash)
        }
        assertTrue { ex.message!!.contains(message) }
        mgmOpsClient.stop()
    }

    @Test
    fun `should fail when response is null`() {
        mgmOpsClient.start()
        setUpRpcSender(null)

        val ex = assertFailsWith<CordaRuntimeException> {
            mgmOpsClient.generateGroupPolicy(shortHash)
        }
        assertTrue { ex.message!!.contains("null") }
        mgmOpsClient.stop()
    }

    @Test
    fun `should fail when request and response has different ids`() {
        mgmOpsClient.start()
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
            mgmOpsClient.generateGroupPolicy(shortHash)
        }
        assertTrue { ex.message!!.contains("ID") }
        mgmOpsClient.stop()
    }

    @Test
    fun `should fail when request and response has different requestTimestamp`() {
        mgmOpsClient.start()
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
            mgmOpsClient.generateGroupPolicy(shortHash)
        }
        assertTrue { ex.message!!.contains("timestamp") }
        mgmOpsClient.stop()
    }

    @Test
    fun `should fail when response type is not the expected type`() {
        mgmOpsClient.start()
        setUpRpcSender("WRONG RESPONSE TYPE")

        val ex = assertFailsWith<CordaRuntimeException> {
            mgmOpsClient.generateGroupPolicy(shortHash)
        }

        assertTrue { ex.message!!.contains("Expected class") }
        mgmOpsClient.stop()
    }

    @Test
    fun `generateGroupPolicy should fail if the member can not be found`() {
        mgmOpsClient.start()
        setUpRpcSender(
            MGMGroupPolicyResponse(
                "groupPolicy",
            )
        )

        assertThrows<CouldNotFindMemberException> {
            mgmOpsClient.generateGroupPolicy(ShortHash.of("000000000000"))
        }
        mgmOpsClient.stop()
    }

    @Test
    fun `generateGroupPolicy should fail if the member can not be read`() {
        mgmOpsClient.start()
        setUpRpcSender(
            MGMGroupPolicyResponse(
                "groupPolicy",
            )
        )
        whenever(groupReader.lookup(mgmX500Name)).doReturn(null)

        assertThrows<CouldNotFindMemberException> {
            mgmOpsClient.generateGroupPolicy(shortHash)
        }
        mgmOpsClient.stop()
    }

    @Test
    fun `generateGroupPolicy should fail if the member is not an mgm`() {
        mgmOpsClient.start()
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
            mgmOpsClient.generateGroupPolicy(shortHash)
        }
        mgmOpsClient.stop()
    }

    @Nested
    inner class AddApprovalRuleTests {
        @Test
        fun `addApprovalRule should send the correct request`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            val params = ApprovalRuleParams(RULE_REGEX, ApprovalRuleType.STANDARD, RULE_LABEL)
            whenever(
                membershipPersistenceClient.addApprovalRule(
                    holdingIdentity,
                    params
                )
            ).doReturn(
                MembershipPersistenceResult.Success(ApprovalRuleDetails(RULE_ID, RULE_REGEX, RULE_LABEL))
            )

            mgmOpsClient.addApprovalRule(
                shortHash,
                params
            )

            verify(membershipPersistenceClient).addApprovalRule(
                holdingIdentity,
                params
            )
            mgmOpsClient.stop()
        }

        @Test
        fun `addApprovalRule should fail if the member cannot be found`() {
            mgmOpsClient.start()
            setUpRpcSender(null)

            assertThrows<CouldNotFindMemberException> {
                mgmOpsClient.addApprovalRule(
                    ShortHash.of("000000000000"),
                    ApprovalRuleParams(RULE_REGEX, ApprovalRuleType.STANDARD, RULE_LABEL)
                )
            }
            mgmOpsClient.stop()
        }

        @Test
        fun `addApprovalRule should fail if the member cannot be read`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            whenever(groupReader.lookup(mgmX500Name)).doReturn(null)

            assertThrows<CouldNotFindMemberException> {
                mgmOpsClient.addApprovalRule(
                    shortHash, ApprovalRuleParams(RULE_REGEX, ApprovalRuleType.STANDARD, RULE_LABEL)
                )
            }
            mgmOpsClient.stop()
        }

        @Test
        fun `addApprovalRule should fail if the member is not the MGM`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmOpsClient.addApprovalRule(
                    shortHash, ApprovalRuleParams(RULE_REGEX, ApprovalRuleType.STANDARD, RULE_LABEL)
                )
            }
            mgmOpsClient.stop()
        }
    }

    @Nested
    inner class DeleteApprovalRuleTests {
        @Test
        fun `deleteApprovalRule should send the correct request`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            whenever(
                membershipPersistenceClient.deleteApprovalRule(
                    holdingIdentity,
                    RULE_ID,
                    RULE_TYPE
                )
            ).doReturn(
                MembershipPersistenceResult.success()
            )

            mgmOpsClient.deleteApprovalRule(
                shortHash,
                RULE_ID,
                RULE_TYPE
            )

            verify(membershipPersistenceClient).deleteApprovalRule(
                holdingIdentity,
                RULE_ID,
                RULE_TYPE
            )
            mgmOpsClient.stop()
        }

        @Test
        fun `deleteApprovalRule should fail if the member cannot be found`() {
            mgmOpsClient.start()
            setUpRpcSender(null)

            assertThrows<CouldNotFindMemberException> {
                mgmOpsClient.deleteApprovalRule(
                    ShortHash.of("000000000000"), RULE_ID, RULE_TYPE
                )
            }
            mgmOpsClient.stop()
        }

        @Test
        fun `deleteApprovalRule should fail if the member cannot be read`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            whenever(groupReader.lookup(mgmX500Name)).doReturn(null)

            assertThrows<CouldNotFindMemberException> {
                mgmOpsClient.deleteApprovalRule(
                    shortHash, RULE_ID, RULE_TYPE
                )
            }
            mgmOpsClient.stop()
        }

        @Test
        fun `deleteApprovalRule should fail if the member is not the MGM`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmOpsClient.deleteApprovalRule(
                    shortHash, RULE_ID, RULE_TYPE
                )
            }
            mgmOpsClient.stop()
        }
    }

    @Nested
    inner class GetApprovalRulesTests {
        @Test
        fun `getApprovalRules should send the correct request`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            whenever(
                membershipQueryClient.getApprovalRules(
                    holdingIdentity,
                    ApprovalRuleType.STANDARD,
                )
            ).doReturn(
                MembershipQueryResult.Success(emptyList())
            )

            mgmOpsClient.getApprovalRules(
                shortHash,
                ApprovalRuleType.STANDARD,
            )

            verify(membershipQueryClient).getApprovalRules(
                holdingIdentity,
                ApprovalRuleType.STANDARD,
            )
            mgmOpsClient.stop()
        }

        @Test
        fun `getApprovalRules should fail if the member cannot be found`() {
            mgmOpsClient.start()
            setUpRpcSender(null)

            assertThrows<CouldNotFindMemberException> {
                mgmOpsClient.getApprovalRules(
                    ShortHash.of("000000000000"), ApprovalRuleType.STANDARD
                )
            }
            mgmOpsClient.stop()
        }

        @Test
        fun `getApprovalRules should fail if the member cannot be read`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            whenever(groupReader.lookup(mgmX500Name)).doReturn(null)

            assertThrows<CouldNotFindMemberException> {
                mgmOpsClient.getApprovalRules(
                    shortHash, ApprovalRuleType.STANDARD
                )
            }
            mgmOpsClient.stop()
        }

        @Test
        fun `getApprovalRules should fail if the member is not the MGM`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmOpsClient.getApprovalRules(
                    shortHash, ApprovalRuleType.STANDARD
                )
            }
            mgmOpsClient.stop()
        }
    }

    @Nested
    inner class ViewRegistrationRequestsTests {
        @Test
        fun `viewRegistrationRequests should send the correct request`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            whenever(
                membershipQueryClient.queryRegistrationRequestsStatus(
                    holdingIdentity,
                    memberName,
                    listOf(RegistrationStatus.PENDING_MANUAL_APPROVAL)
                )
            ).doReturn(
                MembershipQueryResult.Success(emptyList())
            )

            mgmOpsClient.viewRegistrationRequests(
                shortHash,
                memberName,
                false
            )

            verify(membershipQueryClient).queryRegistrationRequestsStatus(
                holdingIdentity,
                memberName,
                listOf(RegistrationStatus.PENDING_MANUAL_APPROVAL)
            )
            mgmOpsClient.stop()
        }

        @Test
        fun `viewRegistrationRequests should fail if the member cannot be found`() {
            mgmOpsClient.start()
            setUpRpcSender(null)

            assertThrows<CouldNotFindMemberException> {
                mgmOpsClient.viewRegistrationRequests(
                    ShortHash.of("000000000000"), memberName, true
                )
            }
            mgmOpsClient.stop()
        }

        @Test
        fun `viewRegistrationRequests should fail if the member cannot be read`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            whenever(groupReader.lookup(mgmX500Name)).doReturn(null)

            assertThrows<CouldNotFindMemberException> {
                mgmOpsClient.viewRegistrationRequests(
                    shortHash, memberName, true
                )
            }
            mgmOpsClient.stop()
        }

        @Test
        fun `viewRegistrationRequests should fail if the member is not the MGM`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmOpsClient.viewRegistrationRequests(
                    shortHash, memberName, true
                )
            }
            mgmOpsClient.stop()
        }
    }

    @Nested
    inner class ReviewRegistrationRequestTests {
        @Test
        fun `reviewRegistrationRequest should publish the correct command when approved`() {
            whenever(coordinator.getManagedResource<Publisher>(any())).doReturn(publisher)
            whenever(publisher.publish(any())).doReturn(listOf(CompletableFuture.completedFuture(Unit)))
            mgmOpsClient.start()
            setUpRpcSender(null)

            mgmOpsClient.reviewRegistrationRequest(
                shortHash,
                REQUEST_ID.uuid(),
                true
            )

            verify(publisher).publish(
                listOf(
                    Record(
                        Schemas.Membership.REGISTRATION_COMMAND_TOPIC,
                        "$REQUEST_ID-$shortHash",
                        RegistrationCommand(ApproveRegistration())
                    )
                )
            )
            mgmOpsClient.stop()
        }

        @Test
        fun `reviewRegistrationRequest should publish the correct command when declined`() {
            whenever(coordinator.getManagedResource<Publisher>(any())).doReturn(publisher)
            whenever(publisher.publish(any())).doReturn(listOf(CompletableFuture.completedFuture(Unit)))
            mgmOpsClient.start()
            setUpRpcSender(null)
            val reason = "sample reason"

            mgmOpsClient.reviewRegistrationRequest(
                shortHash,
                REQUEST_ID.uuid(),
                false,
                reason
            )

            verify(publisher).publish(
                listOf(
                    Record(
                        Schemas.Membership.REGISTRATION_COMMAND_TOPIC,
                        "$REQUEST_ID-$shortHash",
                        RegistrationCommand(DeclineRegistration(reason))
                    )
                )
            )
            mgmOpsClient.stop()
        }

        @Test
        fun `reviewRegistrationRequest should fail if the member cannot be found`() {
            mgmOpsClient.start()
            setUpRpcSender(null)

            assertThrows<CouldNotFindMemberException> {
                mgmOpsClient.reviewRegistrationRequest(
                    ShortHash.of("000000000000"),
                    REQUEST_ID.uuid(),
                    true
                )
            }
            mgmOpsClient.stop()
        }

        @Test
        fun `reviewRegistrationRequest should fail if the member cannot be read`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            whenever(groupReader.lookup(mgmX500Name)).doReturn(null)

            assertThrows<CouldNotFindMemberException> {
                mgmOpsClient.reviewRegistrationRequest(
                    shortHash,
                    REQUEST_ID.uuid(),
                    true
                )
            }
            mgmOpsClient.stop()
        }

        @Test
        fun `reviewRegistrationRequest should fail if the member is not the MGM`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmOpsClient.reviewRegistrationRequest(
                    shortHash,
                    REQUEST_ID.uuid(),
                    true
                )
            }
            mgmOpsClient.stop()
        }
    }

    @Nested
    inner class LifecycleTests {
        @Test
        fun `starting and stopping the service succeeds`() {
            mgmOpsClient.start()
            assertTrue(mgmOpsClient.isRunning)
            mgmOpsClient.stop()
            assertFalse(mgmOpsClient.isRunning)
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

            verify(coordinator).closeManagedResources(argThat {
                size == 3
            })
            verify(coordinator).updateStatus(LifecycleStatus.DOWN, "Handling the stop event for component.")
        }

        @Test
        fun `start will start the coordinator`() {
            mgmOpsClient.start()

            verify(coordinator).start()
        }

        @Test
        fun `stop will stop the coordinator`() {
            mgmOpsClient.stop()

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
                any(), any()
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
                any(), any()
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
            mgmOpsClient.start()

            assertThrows<IllegalStateException> {
                mgmOpsClient.mutualTlsAllowClientCertificate(
                    shortHash,
                    mgmX500Name,
                )
            }
        }

        @Test
        fun `it should fail when not an MGM`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmOpsClient.mutualTlsAllowClientCertificate(
                    shortHash,
                    mgmX500Name,
                )
            }
        }

        @Test
        fun `it should send the correct request`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            whenever(
                membershipPersistenceClient.mutualTlsAddCertificateToAllowedList(
                    holdingIdentity,
                    mgmX500Name.toString(),
                )
            ).doReturn(
                MembershipPersistenceResult.success()
            )

            mgmOpsClient.mutualTlsAllowClientCertificate(
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
            mgmOpsClient.start()

            assertThrows<IllegalStateException> {
                mgmOpsClient.mutualTlsDisallowClientCertificate(
                    shortHash,
                    mgmX500Name,
                )
            }
        }

        @Test
        fun `it should fail when not an MGM`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmOpsClient.mutualTlsDisallowClientCertificate(
                    shortHash,
                    mgmX500Name,
                )
            }
        }

        @Test
        fun `it should send the correct request`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            whenever(
                membershipPersistenceClient.mutualTlsRemoveCertificateFromAllowedList(
                    holdingIdentity,
                    mgmX500Name.toString(),
                )
            ).doReturn(
                MembershipPersistenceResult.success()
            )

            mgmOpsClient.mutualTlsDisallowClientCertificate(
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
            mgmOpsClient.start()

            assertThrows<IllegalStateException> {
                mgmOpsClient.mutualTlsListClientCertificate(
                    shortHash,
                )
            }
        }

        @Test
        fun `it should fail when not an MGM`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmOpsClient.mutualTlsListClientCertificate(
                    shortHash,
                )
            }
        }

        @Test
        fun `it return the correct value`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            whenever(
                membershipQueryClient.mutualTlsListAllowedCertificates(holdingIdentity)
            ).doReturn(
                MembershipQueryResult.Success(
                    listOf(mgmX500Name.toString())
                )
            )

            val subjects = mgmOpsClient.mutualTlsListClientCertificate(
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
            mgmOpsClient.start()

            assertThrows<IllegalStateException> {
                mgmOpsClient.generatePreAuthToken(shortHash, mgmX500Name, null, null)
            }
        }

        @Test
        fun `it should fail when not an MGM`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmOpsClient.generatePreAuthToken(shortHash, mgmX500Name, null, null)
            }
        }

        @Test
        fun `it return the correct value`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            val ttl = Instant.ofEpochSecond(100)
            val ownerX500Name = MemberX500Name.parse("CN=Bob,OU=Unit1,O=Alice,L=London,ST=State1,C=GB")
            val remark = "A remark"
            val uuidCaptor = argumentCaptor<UUID>()
            whenever(
                membershipPersistenceClient.generatePreAuthToken(
                    eq(holdingIdentity), uuidCaptor.capture(), eq(ownerX500Name), eq(ttl), eq(remark)
                )
            ).doReturn(
                MembershipPersistenceResult.success()
            )

            val token = mgmOpsClient.generatePreAuthToken(shortHash, ownerX500Name, ttl, remark)

            assertThat(token).isEqualTo(
                PreAuthToken(uuidCaptor.firstValue.toString(), ownerX500Name.toString(), ttl, PreAuthTokenStatus.AVAILABLE, remark, null)
            )
        }
    }

    @Nested
    inner class GetPreAuthTokensTest {
        @Test
        fun `it should fail when client is not ready`() {
            mgmOpsClient.start()

            assertThrows<IllegalStateException> {
                mgmOpsClient.getPreAuthTokens(shortHash, null, null, false)
            }
        }

        @Test
        fun `it should fail when not an MGM`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmOpsClient.getPreAuthTokens(shortHash, null, null, false)
            }
        }

        @Test
        fun `it return the correct value`() {
            mgmOpsClient.start()
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

            val queryResult = mgmOpsClient.getPreAuthTokens(shortHash, ownerX500Name, tokenId, viewInactive)

            assertThat(queryResult).containsExactly(mockToken1, mockToken2)
        }
    }

    @Nested
    inner class RevokePreAuthToken {
        private val tokenId = UUID.randomUUID()

        @Test
        fun `it should fail when client is not ready`() {
            mgmOpsClient.start()

            assertThrows<IllegalStateException> {
                mgmOpsClient.revokePreAuthToken(shortHash, tokenId, null)
            }
        }

        @Test
        fun `it should fail when not an MGM`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            val mgmContext = mock<MGMContext>()
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mgmContext
            }
            whenever(groupReader.lookup(mgmX500Name)).doReturn(memberInfo)

            assertThrows<MemberNotAnMgmException> {
                mgmOpsClient.revokePreAuthToken(shortHash, tokenId, null)
            }
        }

        @Test
        fun `it return the correct value`() {
            mgmOpsClient.start()
            setUpRpcSender(null)
            val ownerX500Name = MemberX500Name.parse("CN=Bob,OU=Unit1,O=Alice,L=London,ST=State1,C=GB")
            val ttl = Instant.ofEpochSecond(100)
            val token = PreAuthToken(tokenId.toString(), ownerX500Name.toString(), ttl, PreAuthTokenStatus.REVOKED, "", "")
            whenever(
                membershipPersistenceClient.revokePreAuthToken(holdingIdentity, tokenId, null)
            ).doReturn(
                MembershipPersistenceResult.Success(token)
            )

            val revokedToken = mgmOpsClient.revokePreAuthToken(shortHash, tokenId, null)

            assertThat(revokedToken).isEqualTo(token)
        }
    }
}
