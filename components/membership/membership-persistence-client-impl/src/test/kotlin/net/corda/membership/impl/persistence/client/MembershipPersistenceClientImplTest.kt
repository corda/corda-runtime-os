package net.corda.membership.impl.persistence.client

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.ApprovalRuleType.PREAUTH
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.command.AddNotaryToGroupParameters
import net.corda.data.membership.db.request.command.AddPreAuthToken
import net.corda.data.membership.db.request.command.MutualTlsAddToAllowedCertificates
import net.corda.data.membership.db.request.command.MutualTlsRemoveFromAllowedCertificates
import net.corda.data.membership.db.request.command.DeleteApprovalRule
import net.corda.data.membership.db.request.command.PersistApprovalRule
import net.corda.data.membership.db.request.command.PersistGroupParameters
import net.corda.data.membership.db.request.command.PersistGroupPolicy
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.data.membership.db.request.command.RevokePreAuthToken
import net.corda.data.membership.db.request.command.UpdateMemberAndRegistrationRequestToDeclined
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.db.response.MembershipResponseContext
import net.corda.data.membership.db.response.command.DeleteApprovalRuleResponse
import net.corda.data.membership.db.response.command.PersistApprovalRuleResponse
import net.corda.data.membership.db.response.command.PersistGroupParametersResponse
import net.corda.data.membership.db.response.command.PersistGroupPolicyResponse
import net.corda.data.membership.db.response.command.RevokePreAuthTokenResponse
import net.corda.data.membership.db.response.query.PersistenceFailedResponse
import net.corda.data.membership.db.response.query.UpdateMemberAndRegistrationRequestResponse
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.layeredpropertymap.toAvro
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.approval.ApprovalRuleParams
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas.Membership.Companion.MEMBERSHIP_DB_RPC_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

class MembershipPersistenceClientImplTest {
    private companion object {
        const val RULE_ID = "rule-id"
        const val RULE_REGEX = "rule-regex"
        const val RULE_LABEL = "rule-label"
    }

    lateinit var membershipPersistenceClient: MembershipPersistenceClient

    private val lifecycleEventCaptor = argumentCaptor<LifecycleEventHandler>()

    private val expectedConfigs = setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG)

    private val clock = TestClock(Instant.ofEpochSecond(0))

    private val registrationHandle: RegistrationHandle = mock()
    private val configHandle: Resource = mock()
    private val rpcSender: RPCSender<MembershipPersistenceRequest, MembershipPersistenceResponse> = mock()
    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(any()) } doReturn registrationHandle
    }
    private val coordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn coordinator
    }
    private val publisherFactory: PublisherFactory = mock {
        on {
            createRPCSender(
                any<RPCConfig<MembershipPersistenceRequest, MembershipPersistenceResponse>>(),
                any()
            )
        } doReturn rpcSender
    }
    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(eq(coordinator), any()) } doReturn configHandle
    }

    private val ourX500Name = MemberX500Name.parse("O=Alice,L=London,C=GB")
    private val ourGroupId = "Group ID"
    private val ourHoldingIdentity = HoldingIdentity(ourX500Name, ourGroupId)

    private val memberProvidedContext: MemberContext = mock()
    private val mgmProvidedContext: MGMContext = mock()
    private val ourMemberInfo: MemberInfo = mock {
        on { memberProvidedContext } doReturn memberProvidedContext
        on { mgmProvidedContext } doReturn mgmProvidedContext
    }
    private val registrationId = "Group ID 1"
    private val ourRegistrationRequest = RegistrationRequest(
        RegistrationStatus.NEW,
        registrationId,
        ourHoldingIdentity,
        ByteBuffer.wrap("123".toByteArray()),
        CryptoSignatureWithKey(
            ByteBuffer.wrap("456".toByteArray()),
            ByteBuffer.wrap("789".toByteArray()),
            KeyValuePairList(
                listOf(
                    KeyValuePair("key", "value")
                )
            ),
        ),
    )

    private val memberInfoFactory = mock<MemberInfoFactory>()

    private val testConfig =
        SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.parseString("instanceId=1"))

    private fun postStartEvent() {
        lifecycleEventCaptor.firstValue.processEvent(StartEvent(), coordinator)
    }

    private fun postStopEvent() {
        lifecycleEventCaptor.firstValue.processEvent(StopEvent(), coordinator)
    }

    private fun postRegistrationStatusChangeEvent(status: LifecycleStatus) {
        lifecycleEventCaptor.firstValue.processEvent(
            RegistrationStatusChangeEvent(registrationHandle, status),
            coordinator
        )
    }

    private fun postConfigChangedEvent() {
        lifecycleEventCaptor.firstValue.processEvent(
            ConfigChangedEvent(
                setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG),
                mapOf(
                    ConfigKeys.BOOT_CONFIG to testConfig,
                    ConfigKeys.MESSAGING_CONFIG to testConfig
                )
            ),
            coordinator
        )
    }

    @BeforeEach
    fun setUp() {
        membershipPersistenceClient = MembershipPersistenceClientImpl(
            coordinatorFactory,
            publisherFactory,
            configurationReadService,
            memberInfoFactory,
            clock
        )

        verify(coordinatorFactory).createCoordinator(any(), lifecycleEventCaptor.capture())
    }

    @Test
    fun `persist list of member info before starting component`() {
        val result = membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourMemberInfo))

        assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
    }

    @Test
    fun `persist registration request before starting component`() {
        val result = membershipPersistenceClient.persistRegistrationRequest(
            ourHoldingIdentity,
            ourRegistrationRequest
        )

        assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
    }

    @Test
    fun `starting component starts coordinator`() {
        membershipPersistenceClient.start()
        verify(coordinator).start()
    }

    @Test
    fun `stopping component stops coordinator`() {
        membershipPersistenceClient.stop()
        verify(coordinator).stop()
    }

    @Test
    fun `registration handle created and closed as expected`() {
        postStartEvent()

        verify(registrationHandle, never()).close()
        verify(coordinator).followStatusChangesByName(any())

        postStartEvent()
        verify(registrationHandle).close()
        verify(coordinator, times(2)).followStatusChangesByName(any())

        postStopEvent()
        verify(registrationHandle, times(2)).close()
    }

    @Test
    fun `stop event sets status to down but closes no handlers since they haven't been created`() {
        postStopEvent()

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        verify(registrationHandle, never()).close()
        verify(configHandle, never()).close()
        verify(rpcSender, never()).close()
    }

    @Test
    fun `component handles registration change as expected`() {
        postRegistrationStatusChangeEvent(LifecycleStatus.UP)

        val configCaptor = argumentCaptor<Set<String>>()

        verify(configHandle, never()).close()
        verify(configurationReadService).registerComponentForUpdates(
            eq(coordinator),
            configCaptor.capture()
        )
        assertThat(configCaptor.firstValue).isEqualTo(expectedConfigs)

        postRegistrationStatusChangeEvent(LifecycleStatus.UP)
        verify(configHandle).close()
        verify(configurationReadService, times(2)).registerComponentForUpdates(
            eq(coordinator),
            any()
        )

        postRegistrationStatusChangeEvent(LifecycleStatus.DOWN)
        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())

        postRegistrationStatusChangeEvent(LifecycleStatus.ERROR)
        verify(coordinator, times(2)).updateStatus(eq(LifecycleStatus.DOWN), any())

        postStopEvent()
        verify(configHandle, times(2)).close()
    }

    @Test
    fun `config change event handled as expected`() {
        postConfigChangedEvent()

        val argCaptor = argumentCaptor<RPCConfig<MembershipPersistenceRequest, MembershipPersistenceResponse>>()
        verify(rpcSender, never()).close()
        verify(rpcSender).start()
        verify(publisherFactory).createRPCSender(argCaptor.capture(), any())
        verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())

        assertThat(argCaptor.firstValue.requestTopic).isEqualTo(MEMBERSHIP_DB_RPC_TOPIC)
        assertThat(argCaptor.firstValue.requestType).isEqualTo(MembershipPersistenceRequest::class.java)
        assertThat(argCaptor.firstValue.responseType).isEqualTo(MembershipPersistenceResponse::class.java)

        postConfigChangedEvent()
        verify(rpcSender).close()
        verify(rpcSender, times(2)).start()
        verify(publisherFactory, times(2)).createRPCSender(argCaptor.capture(), any())
        verify(coordinator, times(2)).updateStatus(eq(LifecycleStatus.UP), any())

        postStopEvent()
        verify(rpcSender, times(2)).close()
    }

    private fun buildResponse(
        rsContext: MembershipResponseContext,
        payload: Any?
    ) = MembershipPersistenceResponse(
        rsContext,
        payload,
    )

    fun mockPersistenceResponse(
        payload: Any? = null,
        reqTimestampOverride: Instant? = null,
        reqIdOverride: String? = null,
        rsTimestampOverride: Instant? = null,
        holdingIdentityOverride: net.corda.data.identity.HoldingIdentity? = null,
    ) {
        whenever(rpcSender.sendRequest(any())).thenAnswer {
            val rsContext = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                MembershipResponseContext(
                    reqTimestampOverride ?: requestTimestamp,
                    reqIdOverride ?: requestId,
                    rsTimestampOverride ?: clock.instant(),
                    holdingIdentityOverride ?: holdingIdentity
                )
            }
            CompletableFuture.completedFuture(
                buildResponse(
                    rsContext,
                    payload,
                )
            )
        }
    }

    @Test
    fun `request to persistence list of member infos is as expected`() {
        postConfigChangedEvent()
        mockPersistenceResponse()

        membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourMemberInfo))

        with(argumentCaptor<MembershipPersistenceRequest>()) {
            verify(rpcSender).sendRequest(capture())

            assertThat(firstValue.context.requestTimestamp).isBeforeOrEqualTo(clock.instant())
            assertThat(firstValue.context.holdingIdentity)
                .isEqualTo(ourHoldingIdentity.toAvro())

            assertThat(firstValue.request).isInstanceOf(PersistMemberInfo::class.java)
            assertThat((firstValue.request as PersistMemberInfo).members)
                .isNotEmpty
                .isEqualTo(
                    listOf(
                        PersistentMemberInfo(
                            ourHoldingIdentity.toAvro(),
                            KeyValuePairList(emptyList()),
                            KeyValuePairList(emptyList())
                        )
                    )
                )
        }
    }

    @Test
    fun `request to persistence registration request is as expected`() {
        postConfigChangedEvent()
        mockPersistenceResponse()

        membershipPersistenceClient.persistRegistrationRequest(ourHoldingIdentity, ourRegistrationRequest)

        with(argumentCaptor<MembershipPersistenceRequest>()) {
            verify(rpcSender).sendRequest(capture())

            assertThat(firstValue.context.requestTimestamp).isBeforeOrEqualTo(clock.instant())
            assertThat(firstValue.context.holdingIdentity)
                .isEqualTo(ourHoldingIdentity.toAvro())

            assertThat(firstValue.request).isInstanceOf(PersistRegistrationRequest::class.java)
            assertThat((firstValue.request as PersistRegistrationRequest).status)
                .isEqualTo(RegistrationStatus.NEW)
            with((firstValue.request as PersistRegistrationRequest).registrationRequest) {
                assertThat(registrationId)
                    .isEqualTo(this@MembershipPersistenceClientImplTest.registrationId)
            }
        }
    }

    @Test
    fun `successful response for list of member info is correct`() {
        postConfigChangedEvent()
        mockPersistenceResponse()

        val result = membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourMemberInfo))
        assertThat(result).isInstanceOf(MembershipPersistenceResult.Success::class.java)
    }

    @Test
    fun `failed response for list of member info is correct`() {
        postConfigChangedEvent()
        mockPersistenceResponse(PersistenceFailedResponse("Placeholder error"), null)

        val result = membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourMemberInfo))
        assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
    }

    @Test
    fun `Mismatch in holding identity between RQ and RS causes failed response`() {
        postConfigChangedEvent()
        mockPersistenceResponse(
            holdingIdentityOverride = net.corda.data.identity.HoldingIdentity("O=BadName,L=London,C=GB", "BAD_ID")
        )
        val result = membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourMemberInfo))
        assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
    }

    @Test
    fun `Mismatch in request timestamp between RQ and RS causes failed response`() {
        postConfigChangedEvent()
        mockPersistenceResponse(
            reqTimestampOverride = clock.instant().plusSeconds(5)
        )
        val result = membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourMemberInfo))
        assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
    }

    @Test
    fun `Mismatch in request ID between RQ and RS causes failed response`() {
        postConfigChangedEvent()
        mockPersistenceResponse(
            reqIdOverride = "Group ID 3"
        )
        val result = membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourMemberInfo))
        assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
    }

    @Test
    fun `Response timestamp before request timestamp causes failed response`() {
        postConfigChangedEvent()
        mockPersistenceResponse(
            rsTimestampOverride = clock.instant().minusSeconds(10)
        )
        val result = membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourMemberInfo))
        assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
    }

    @Test
    fun `persistGroupPolicy return the correct version`() {
        val groupPolicy = mock<LayeredPropertyMap>()
        postConfigChangedEvent()
        mockPersistenceResponse(
            PersistGroupPolicyResponse(103),
        )

        val result = membershipPersistenceClient.persistGroupPolicy(ourHoldingIdentity, groupPolicy)

        assertThat(result).isEqualTo(MembershipPersistenceResult.Success(103))
    }

    @Test
    fun `persistGroupPolicy returns error in case of failure`() {
        val groupPolicy = mock<LayeredPropertyMap>()
        postConfigChangedEvent()
        mockPersistenceResponse(
            PersistenceFailedResponse("Placeholder error"),
        )

        val result = membershipPersistenceClient.persistGroupPolicy(ourHoldingIdentity, groupPolicy)

        assertThat(result).isEqualTo(MembershipPersistenceResult.Failure<Int>("Placeholder error"))
    }

    @Test
    fun `persistGroupPolicy return failure for unexpected result`() {
        val groupPolicy = mock<LayeredPropertyMap>()
        postConfigChangedEvent()
        mockPersistenceResponse(
            null,
        )

        val result = membershipPersistenceClient.persistGroupPolicy(ourHoldingIdentity, groupPolicy)

        assertThat(result).isEqualTo(MembershipPersistenceResult.Failure<Int>("Unexpected response: null"))
    }

    @Test
    fun `persistGroupPolicy send the correct data`() {
        val groupPolicyEntries = mapOf("a" to "b").entries
        val groupPolicy = mock<LayeredPropertyMap> {
            on { entries } doReturn groupPolicyEntries
        }
        postConfigChangedEvent()
        val argument = argumentCaptor<MembershipPersistenceRequest>()
        val response = CompletableFuture.completedFuture(mock<MembershipPersistenceResponse>())
        whenever(rpcSender.sendRequest(argument.capture())).thenReturn(response)

        membershipPersistenceClient.persistGroupPolicy(ourHoldingIdentity, groupPolicy)

        val properties = (argument.firstValue.request as? PersistGroupPolicy)?.properties?.items
        assertThat(properties).containsExactly(
            KeyValuePair("a", "b")
        )
    }

    @Test
    fun `update registration request status is as expected`() {
        postConfigChangedEvent()
        mockPersistenceResponse()
        val result = membershipPersistenceClient.setRegistrationRequestStatus(
            ourHoldingIdentity,
            registrationId,
            RegistrationStatus.DECLINED
        )
        assertThat(result).isInstanceOf(MembershipPersistenceResult.Success::class.java)
    }

    @Nested
    inner class PersistGroupParametersInitialSnapshotTests {
        @Test
        fun `persistGroupParametersInitialSnapshot returns the correct epoch`() {
            postConfigChangedEvent()
            val mockGroupParameters = KeyValuePairList(
                listOf(
                    KeyValuePair(EPOCH_KEY, "1"),
                )
            )
            mockPersistenceResponse(
                PersistGroupParametersResponse(mockGroupParameters),
            )

            val result = membershipPersistenceClient.persistGroupParametersInitialSnapshot(ourHoldingIdentity)

            assertThat(result).isEqualTo(MembershipPersistenceResult.Success(mockGroupParameters))
        }

        @Test
        fun `persistGroupParametersInitialSnapshot returns error in case of failure`() {
            postConfigChangedEvent()
            mockPersistenceResponse(
                PersistenceFailedResponse("Placeholder error"),
            )

            val result = membershipPersistenceClient.persistGroupParametersInitialSnapshot(ourHoldingIdentity)

            assertThat(result).isEqualTo(MembershipPersistenceResult.Failure<KeyValuePairList>("Placeholder error"))
        }

        @Test
        fun `persistGroupParametersInitialSnapshot returns failure for unexpected result`() {
            postConfigChangedEvent()
            mockPersistenceResponse(
                null,
            )

            val result = membershipPersistenceClient.persistGroupParametersInitialSnapshot(ourHoldingIdentity)

            assertThat(result).isEqualTo(MembershipPersistenceResult.Failure<KeyValuePairList>("Unexpected response: null"))
        }
    }

    @Nested
    inner class PersistGroupParametersTests {
        @Test
        fun `persistGroupParameters returns the correct epoch`() {
            val groupParameters = mock<GroupParameters> {
                on { entries } doReturn mapOf(EPOCH_KEY to "5").entries
            }
            postConfigChangedEvent()
            mockPersistenceResponse(
                PersistGroupParametersResponse(groupParameters.toAvro()),
            )

            val result = membershipPersistenceClient.persistGroupParameters(ourHoldingIdentity, groupParameters)

            assertThat(result).isEqualTo(MembershipPersistenceResult.Success(groupParameters.toAvro()))
        }

        @Test
        fun `persistGroupParameters returns error in case of failure`() {
            val groupParameters = mock<GroupParameters>()
            postConfigChangedEvent()
            mockPersistenceResponse(
                PersistenceFailedResponse("Placeholder error"),
            )

            val result = membershipPersistenceClient.persistGroupParameters(ourHoldingIdentity, groupParameters)

            assertThat(result).isEqualTo(MembershipPersistenceResult.Failure<KeyValuePairList>("Placeholder error"))
        }

        @Test
        fun `persistGroupParameters returns failure for unexpected result`() {
            val groupParameters = mock<GroupParameters>()
            postConfigChangedEvent()
            mockPersistenceResponse(
                null,
            )

            val result = membershipPersistenceClient.persistGroupParameters(ourHoldingIdentity, groupParameters)

            assertThat(result).isEqualTo(MembershipPersistenceResult.Failure<KeyValuePairList>("Unexpected response: null"))
        }

        @Test
        fun `persistGroupParameters sends the correct data`() {
            val groupParameterEntries = mapOf("a" to "b").entries
            val groupParameters = mock<GroupParameters> {
                on { entries } doReturn groupParameterEntries
            }
            postConfigChangedEvent()
            val argument = argumentCaptor<MembershipPersistenceRequest>()
            val response = CompletableFuture.completedFuture(mock<MembershipPersistenceResponse>())
            whenever(rpcSender.sendRequest(argument.capture())).thenReturn(response)

            membershipPersistenceClient.persistGroupParameters(ourHoldingIdentity, groupParameters)

            val parameters = (argument.firstValue.request as? PersistGroupParameters)?.groupParameters?.items
            assertThat(parameters).containsExactly(
                KeyValuePair("a", "b")
            )
        }
    }

    @Nested
    inner class AddNotaryToGroupParametersTests {
        @Test
        fun `addNotaryToGroupParameters returns the correct epoch`() {
            val notary = ourMemberInfo
            val mockGroupParameters = mock<KeyValuePairList>()
            postConfigChangedEvent()
            mockPersistenceResponse(
                PersistGroupParametersResponse(mockGroupParameters),
            )

            val result = membershipPersistenceClient.addNotaryToGroupParameters(ourHoldingIdentity, notary)

            assertThat(result).isEqualTo(MembershipPersistenceResult.Success(mockGroupParameters))
        }

        @Test
        fun `addNotaryToGroupParameters returns error in case of failure`() {
            val notary = ourMemberInfo
            postConfigChangedEvent()
            mockPersistenceResponse(
                PersistenceFailedResponse("Placeholder error"),
            )

            val result = membershipPersistenceClient.addNotaryToGroupParameters(ourHoldingIdentity, notary)

            assertThat(result).isEqualTo(MembershipPersistenceResult.Failure<KeyValuePairList>("Placeholder error"))
        }

        @Test
        fun `addNotaryToGroupParameters returns failure for unexpected result`() {
            val notary = ourMemberInfo
            postConfigChangedEvent()
            mockPersistenceResponse(
                null,
            )

            val result = membershipPersistenceClient.addNotaryToGroupParameters(ourHoldingIdentity, notary)

            assertThat(result).isEqualTo(MembershipPersistenceResult.Failure<KeyValuePairList>("Unexpected response: null"))
        }

        @Test
        fun `addNotaryToGroupParameters sends the correct data`() {
            val memberContext: MemberContext = mock {
                on { entries } doReturn mapOf("a" to "b").entries
            }
            val mgmContext: MGMContext = mock {
                on { entries } doReturn mapOf("c" to "d").entries
            }
            val notaryInRequest: MemberInfo = mock {
                on { memberProvidedContext } doReturn memberContext
                on { mgmProvidedContext } doReturn mgmContext
            }
            postConfigChangedEvent()
            val argument = argumentCaptor<MembershipPersistenceRequest>()
            val response = CompletableFuture.completedFuture(mock<MembershipPersistenceResponse>())
            whenever(rpcSender.sendRequest(argument.capture())).thenReturn(response)

            membershipPersistenceClient.addNotaryToGroupParameters(ourHoldingIdentity, notaryInRequest)

            val notary = (argument.firstValue.request as? AddNotaryToGroupParameters)?.notary
            assertThat(notary?.viewOwningMember).isEqualTo(ourHoldingIdentity.toAvro())
            assertThat(notary?.memberContext?.items).containsExactly(KeyValuePair("a", "b"))
            assertThat(notary?.mgmContext?.items).containsExactly(KeyValuePair("c", "d"))
        }
    }

    @Nested
    inner class AddApprovalRuleTests {
        @Test
        fun `addApprovalRule returns the correct result`() {
            postConfigChangedEvent()
            val expectedResult = ApprovalRuleDetails(RULE_ID, RULE_REGEX, RULE_LABEL)
            mockPersistenceResponse(
                PersistApprovalRuleResponse(expectedResult),
            )

            val result = membershipPersistenceClient.addApprovalRule(
                ourHoldingIdentity,
                ApprovalRuleParams(RULE_REGEX, ApprovalRuleType.STANDARD, RULE_LABEL)
            )

            assertThat(result.getOrThrow()).isEqualTo(expectedResult)
        }

        @Test
        fun `addApprovalRule returns error in case of failure`() {
            postConfigChangedEvent()
            mockPersistenceResponse(
                PersistenceFailedResponse("Placeholder error"),
            )

            val result = membershipPersistenceClient.addApprovalRule(
                ourHoldingIdentity,
                ApprovalRuleParams(RULE_REGEX, ApprovalRuleType.STANDARD, RULE_LABEL)
            )

            assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }

        @Test
        fun `addApprovalRule returns failure for unexpected result`() {
            postConfigChangedEvent()
            mockPersistenceResponse(
                null,
            )

            val result = membershipPersistenceClient.addApprovalRule(
                ourHoldingIdentity,
                ApprovalRuleParams(RULE_REGEX, ApprovalRuleType.STANDARD, RULE_LABEL)
            )

            assertThat(result).isEqualTo(MembershipPersistenceResult.Failure<ApprovalRuleDetails>("Unexpected response: null"))
        }

        @Test
        fun `addApprovalRule sends the correct data`() {
            postConfigChangedEvent()
            val argument = argumentCaptor<MembershipPersistenceRequest>()
            val response = CompletableFuture.completedFuture(mock<MembershipPersistenceResponse>())
            whenever(rpcSender.sendRequest(argument.capture())).thenReturn(response)

            membershipPersistenceClient.addApprovalRule(
                ourHoldingIdentity,
                ApprovalRuleParams(RULE_REGEX, ApprovalRuleType.STANDARD, RULE_LABEL)
            )

            val sentRequest = (argument.firstValue.request as? PersistApprovalRule)!!
            assertThat(sentRequest.rule).isEqualTo(RULE_REGEX)
            assertThat(sentRequest.ruleType).isEqualTo(ApprovalRuleType.STANDARD)
            assertThat(sentRequest.label).isEqualTo(RULE_LABEL)
        }
    }

    @Nested
    inner class DeleteApprovalRuleTests {
        @Test
        fun `deleteApprovalRule returns the correct ID`() {
            postConfigChangedEvent()
            mockPersistenceResponse(
                DeleteApprovalRuleResponse(),
            )

            val result = membershipPersistenceClient.deleteApprovalRule(
                ourHoldingIdentity,
                RULE_ID,
                PREAUTH
            )

            assertThat(result).isEqualTo(MembershipPersistenceResult.success())
        }

        @Test
        fun `deleteApprovalRule returns error in case of failure`() {
            postConfigChangedEvent()
            mockPersistenceResponse(
                PersistenceFailedResponse("Placeholder error"),            )

            val result = membershipPersistenceClient.deleteApprovalRule(
                ourHoldingIdentity,
                RULE_ID,
                PREAUTH
            )

            assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }

        @Test
        fun `deleteApprovalRule sends the correct data`() {
            postConfigChangedEvent()
            val argument = argumentCaptor<MembershipPersistenceRequest>()
            val response = CompletableFuture.completedFuture(mock<MembershipPersistenceResponse>())
            whenever(rpcSender.sendRequest(argument.capture())).thenReturn(response)

            membershipPersistenceClient.deleteApprovalRule(
                ourHoldingIdentity,
                RULE_ID,
                PREAUTH
            )

            val sentRequest = (argument.firstValue.request as? DeleteApprovalRule)!!
            assertThat(sentRequest.ruleId).isEqualTo(RULE_ID)
            assertThat(sentRequest.ruleType).isEqualTo(PREAUTH)
        }
    }

    @Nested
    inner class SetMemberAndRegistrationRequestAsApprovedTests {
        @Test
        fun `it returns the correct member info`() {
            val bob = createTestHoldingIdentity("O=Bob ,L=London, C=GB", ourGroupId)
            val persistentMemberInfo = PersistentMemberInfo(
                bob.toAvro(),
                KeyValuePairList(emptyList()),
                KeyValuePairList(emptyList())
            )
            val memberInfo = mock<MemberInfo>()
            whenever(memberInfoFactory.create(persistentMemberInfo)).doReturn(memberInfo)
            val registrationRequestId = "registrationRequestId"
            postConfigChangedEvent()
            mockPersistenceResponse(
                payload = UpdateMemberAndRegistrationRequestResponse(
                    persistentMemberInfo
                )
            )

            val result = membershipPersistenceClient.setMemberAndRegistrationRequestAsApproved(
                ourHoldingIdentity,
                bob,
                registrationRequestId,
            )

            assertThat(result.getOrThrow()).isSameAs(memberInfo)
        }

        @Test
        fun `it returns error when there was an issue`() {
            val bob = createTestHoldingIdentity("O=Bob ,L=London, C=GB", ourGroupId)
            val registrationRequestId = "registrationRequestId"
            postConfigChangedEvent()
            mockPersistenceResponse(false)

            val result = membershipPersistenceClient.setMemberAndRegistrationRequestAsApproved(
                ourHoldingIdentity,
                bob,
                registrationRequestId,
            )

            assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }

        @Test
        fun `it returns error when the return data has the wrong type`() {
            val bob = createTestHoldingIdentity("O=Bob ,L=London, C=GB", ourGroupId)
            val registrationRequestId = "registrationRequestId"
            postConfigChangedEvent()
            mockPersistenceResponse(payload = "This should not be a string!")

            val result = membershipPersistenceClient.setMemberAndRegistrationRequestAsApproved(
                ourHoldingIdentity,
                bob,
                registrationRequestId,
            )

            assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }
    }

    @Nested
    inner class SetMemberAndRegistrationRequestAsDeclinedTests {
        @Test
        fun `request to set member and request as declined is as expected`() {
            val bob = createTestHoldingIdentity("O=Bob ,L=London, C=GB", ourGroupId)
            val registrationRequestId = "registrationRequestId"

            postConfigChangedEvent()
            mockPersistenceResponse()

            membershipPersistenceClient.setMemberAndRegistrationRequestAsDeclined(
                ourHoldingIdentity,
                bob,
                registrationRequestId
            )

            with(argumentCaptor<MembershipPersistenceRequest>()) {
                verify(rpcSender).sendRequest(capture())

                assertThat(firstValue.context.requestTimestamp).isBeforeOrEqualTo(clock.instant())
                assertThat(firstValue.context.holdingIdentity)
                    .isEqualTo(ourHoldingIdentity.toAvro())

                assertThat(firstValue.request).isInstanceOf(UpdateMemberAndRegistrationRequestToDeclined::class.java)
                with (firstValue.request as UpdateMemberAndRegistrationRequestToDeclined) {
                    assertThat(member).isEqualTo(bob.toAvro())
                    assertThat(registrationId).isEqualTo(registrationRequestId)
                }

            }
        }

        @Test
        fun `it returns error when there was an issue`() {
            val bob = createTestHoldingIdentity("O=Bob ,L=London, C=GB", ourGroupId)
            val registrationRequestId = "registrationRequestId"
            postConfigChangedEvent()
            mockPersistenceResponse(false)

            val result = membershipPersistenceClient.setMemberAndRegistrationRequestAsDeclined(
                ourHoldingIdentity,
                bob,
                registrationRequestId,
            )

            assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }

        @Test
        fun `it returns error when the return data has the wrong type`() {
            val bob = createTestHoldingIdentity("O=Bob ,L=London, C=GB", ourGroupId)
            val registrationRequestId = "registrationRequestId"
            postConfigChangedEvent()
            mockPersistenceResponse(payload = "This should not be a string!")

            val result = membershipPersistenceClient.setMemberAndRegistrationRequestAsDeclined(
                ourHoldingIdentity,
                bob,
                registrationRequestId,
            )

            assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }
    }

    @Nested
    inner class MutualTlsCommandsTests {
        @Test
        fun `mutualTlsAddCertificateToAllowedList sends the correct request`() {
            postConfigChangedEvent()
            val argument = argumentCaptor<MembershipPersistenceRequest>()
            val response = CompletableFuture.completedFuture(mock<MembershipPersistenceResponse>())
            whenever(rpcSender.sendRequest(argument.capture())).thenReturn(response)

            membershipPersistenceClient.mutualTlsAddCertificateToAllowedList(
                ourHoldingIdentity,
                ourX500Name.toString(),
            )

            assertThat(argument.firstValue.request).isInstanceOf(MutualTlsAddToAllowedCertificates::class.java)
        }

        @Test
        fun `mutualTlsAddCertificateToAllowedList return success after success`() {
            postConfigChangedEvent()
            mockPersistenceResponse()

            val response = membershipPersistenceClient.mutualTlsAddCertificateToAllowedList(
                ourHoldingIdentity,
                ourX500Name.toString(),
            )

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        }

        @Test
        fun `mutualTlsAddCertificateToAllowedList return failure after failure`() {
            postConfigChangedEvent()
            mockPersistenceResponse(PersistenceFailedResponse("Placeholder error"))

            val response = membershipPersistenceClient.mutualTlsAddCertificateToAllowedList(
                ourHoldingIdentity,
                ourX500Name.toString(),
            )

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }

        @Test
        fun `mutualTlsAddCertificateToAllowedList return failure after unknown result`() {
            postConfigChangedEvent()
            mockPersistenceResponse("Placeholder error")

            val response = membershipPersistenceClient.mutualTlsAddCertificateToAllowedList(
                ourHoldingIdentity,
                ourX500Name.toString(),
            )

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }

        @Test
        fun `mutualTlsRemoveCertificateFromAllowedList sends the correct request`() {
            postConfigChangedEvent()
            val argument = argumentCaptor<MembershipPersistenceRequest>()
            val response = CompletableFuture.completedFuture(mock<MembershipPersistenceResponse>())
            whenever(rpcSender.sendRequest(argument.capture())).thenReturn(response)

            membershipPersistenceClient.mutualTlsRemoveCertificateFromAllowedList(
                ourHoldingIdentity,
                ourX500Name.toString(),
            )

            assertThat(argument.firstValue.request).isInstanceOf(MutualTlsRemoveFromAllowedCertificates::class.java)
        }

        @Test
        fun `mutualTlsRemoveCertificateFromAllowedList return success after success`() {
            postConfigChangedEvent()
            mockPersistenceResponse()

            val response = membershipPersistenceClient.mutualTlsRemoveCertificateFromAllowedList(
                ourHoldingIdentity,
                ourX500Name.toString(),
            )

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        }

        @Test
        fun `mutualTlsRemoveCertificateFromAllowedList return failure after failure`() {
            postConfigChangedEvent()
            mockPersistenceResponse(PersistenceFailedResponse("Placeholder error"))

            val response = membershipPersistenceClient.mutualTlsRemoveCertificateFromAllowedList(
                ourHoldingIdentity,
                ourX500Name.toString(),
            )

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }

        @Test
        fun `mutualTlsRemoveCertificateFromAllowedList return failure after unknown result`() {
            postConfigChangedEvent()
            mockPersistenceResponse("Placeholder error")

            val response = membershipPersistenceClient.mutualTlsRemoveCertificateFromAllowedList(
                ourHoldingIdentity,
                ourX500Name.toString(),
            )

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }
    }

    @Nested
    inner class PreAuthTokenTests {
        private val uuid = UUID.randomUUID()
        private val ttl = Instant.ofEpochSecond(100)
        private val remarks = "a remark"
        private val removalRemark = "another remark"

        @Test
        fun `generatePreAuthToken sends the correct request`() {
            postConfigChangedEvent()
            val argument = argumentCaptor<MembershipPersistenceRequest>()
            val response = CompletableFuture.completedFuture(mock<MembershipPersistenceResponse>())
            whenever(rpcSender.sendRequest(argument.capture())).thenReturn(response)

            membershipPersistenceClient.generatePreAuthToken(ourHoldingIdentity, uuid, ourX500Name, ttl, remarks)

            assertThat(argument.firstValue.request).isInstanceOf(AddPreAuthToken::class.java)
            val request = (argument.firstValue.request as AddPreAuthToken)
            assertThat(request.tokenId).isEqualTo(uuid.toString())
            assertThat(request.remark).isEqualTo(remarks)
            assertThat(request.ttl).isEqualTo(ttl)
            assertThat(request.ownerX500Name).isEqualTo(ourX500Name.toString())
        }

        @Test
        fun `generatePreAuthToken returns the token correctly`() {
            postConfigChangedEvent()
            mockPersistenceResponse()

            val response = membershipPersistenceClient.generatePreAuthToken(ourHoldingIdentity, uuid, ourX500Name, ttl, remarks)

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        }

        @Test
        fun `generatePreAuthToken return failure after failure`() {
            postConfigChangedEvent()
            mockPersistenceResponse(PersistenceFailedResponse("Placeholder error"))

            val response = membershipPersistenceClient.generatePreAuthToken(ourHoldingIdentity, uuid, ourX500Name, ttl, remarks)

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }

        @Test
        fun `generatePreAuthToken return failure after unknown result`() {
            postConfigChangedEvent()
            mockPersistenceResponse("Placeholder error")

            val response = membershipPersistenceClient.generatePreAuthToken(ourHoldingIdentity, uuid, ourX500Name, ttl, remarks)

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }

        @Test
        fun `revokePreAuthToken sends the correct request`() {
            postConfigChangedEvent()
            val argument = argumentCaptor<MembershipPersistenceRequest>()
            val response = CompletableFuture.completedFuture(mock<MembershipPersistenceResponse>())
            whenever(rpcSender.sendRequest(argument.capture())).thenReturn(response)

            membershipPersistenceClient.revokePreAuthToken(ourHoldingIdentity, uuid, removalRemark)

            assertThat(argument.firstValue.request).isInstanceOf(RevokePreAuthToken::class.java)
            val request = (argument.firstValue.request as RevokePreAuthToken)
            assertThat(request.tokenId).isEqualTo(uuid.toString())
            assertThat(request.remark).isEqualTo(removalRemark)
        }

        @Test
        fun `revokePreAuthToken returns the token correctly`() {
            postConfigChangedEvent()
            val mockToken = mock<PreAuthToken>()
            mockPersistenceResponse(RevokePreAuthTokenResponse(mockToken))

            val response = membershipPersistenceClient.revokePreAuthToken(ourHoldingIdentity, uuid, removalRemark).getOrThrow()

            assertThat(response).isEqualTo(mockToken)
        }

        @Test
        fun `revokePreAuthToken return failure after failure`() {
            postConfigChangedEvent()
            mockPersistenceResponse(PersistenceFailedResponse("Placeholder error"))

            val response = membershipPersistenceClient.revokePreAuthToken(ourHoldingIdentity, uuid, removalRemark)

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }

        @Test
        fun `revokePreAuthToken return failure after unknown result`() {
            postConfigChangedEvent()
            mockPersistenceResponse("Placeholder error")

            val response = membershipPersistenceClient.revokePreAuthToken(ourHoldingIdentity, uuid, removalRemark)

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }
    }
}
