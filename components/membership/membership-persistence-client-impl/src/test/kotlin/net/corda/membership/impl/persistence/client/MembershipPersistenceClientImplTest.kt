package net.corda.membership.impl.persistence.client

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedData
import net.corda.data.membership.StaticNetworkInfo
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.ApprovalRuleType.PREAUTH
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.command.ActivateMember
import net.corda.data.membership.db.request.command.AddNotaryToGroupParameters
import net.corda.data.membership.db.request.command.AddPreAuthToken
import net.corda.data.membership.db.request.command.ConsumePreAuthToken
import net.corda.data.membership.db.request.command.DeleteApprovalRule
import net.corda.data.membership.db.request.command.MutualTlsAddToAllowedCertificates
import net.corda.data.membership.db.request.command.MutualTlsRemoveFromAllowedCertificates
import net.corda.data.membership.db.request.command.PersistApprovalRule
import net.corda.data.membership.db.request.command.PersistGroupParameters
import net.corda.data.membership.db.request.command.PersistGroupPolicy
import net.corda.data.membership.db.request.command.PersistHostedIdentity
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.data.membership.db.request.command.RevokePreAuthToken
import net.corda.data.membership.db.request.command.SessionKeyAndCertificate
import net.corda.data.membership.db.request.command.SuspendMember
import net.corda.data.membership.db.request.command.UpdateGroupParameters
import net.corda.data.membership.db.request.command.UpdateStaticNetworkInfo
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.db.response.MembershipResponseContext
import net.corda.data.membership.db.response.command.ActivateMemberResponse
import net.corda.data.membership.db.response.command.DeleteApprovalRuleResponse
import net.corda.data.membership.db.response.command.PersistApprovalRuleResponse
import net.corda.data.membership.db.response.command.PersistGroupParametersResponse
import net.corda.data.membership.db.response.command.PersistHostedIdentityResponse
import net.corda.data.membership.db.response.command.RevokePreAuthTokenResponse
import net.corda.data.membership.db.response.command.SuspendMemberResponse
import net.corda.data.membership.db.response.query.ErrorKind
import net.corda.data.membership.db.response.query.PersistenceFailedResponse
import net.corda.data.membership.db.response.query.StaticNetworkInfoQueryResponse
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
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.membership.lib.SignedGroupParameters
import net.corda.membership.lib.approval.ApprovalRuleParams
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas.Membership.MEMBERSHIP_DB_RPC_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import net.corda.data.membership.SignedGroupParameters as AvroGroupParameters

class MembershipPersistenceClientImplTest {
    private companion object {
        const val RULE_ID = "rule-id"
        const val RULE_REGEX = "rule-regex"
        const val RULE_LABEL = "rule-label"
        const val SERIAL = 5L
        const val REASON = "test"
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
    private val bobX500Name = MemberX500Name.parse("O=Bob,L=London,C=GB")
    private val groupParametersFactory = mock<GroupParametersFactory>()

    private val memberProvidedContext: MemberContext = mock()
    private val mgmProvidedContext: MGMContext = mock()
    private val signature = CryptoSignatureWithKey(
        ByteBuffer.wrap("456".toByteArray()),
        ByteBuffer.wrap("789".toByteArray()),
    )
    private val signatureSpec = CryptoSignatureSpec(null, null, null)
    private val ourPersistentMemberInfo = PersistentMemberInfo(
        ourHoldingIdentity.toAvro(),
        null,
        null,
        SignedData(
            memberProvidedContext.toAvro().toByteBuffer(),
            signature,
            signatureSpec,
        ),
        mgmProvidedContext.toAvro().toByteBuffer(),
    )
    private val ourSignedMemberInfo = mock<SelfSignedMemberInfo> {
        on { memberContextBytes } doReturn byteArrayOf(1)
        on { mgmContextBytes } doReturn byteArrayOf(2)
        on { memberSignature } doReturn signature
        on { memberSignatureSpec } doReturn signatureSpec
    }
    private val registrationId = "Group ID 1"
    private val ourRegistrationRequest = RegistrationRequest(
        RegistrationStatus.SENT_TO_MGM,
        registrationId,
        ourHoldingIdentity,
        SignedData(
            ByteBuffer.wrap("123".toByteArray()),
            signature,
            signatureSpec,
        ),
        SignedData(
            ByteBuffer.wrap("456".toByteArray()),
            signature,
            signatureSpec,
        ),
        0L,
    )

    private val memberInfoFactory = mock<MemberInfoFactory>()
    private val serialisedParams = "serialised-params".toByteArray()

    private val publicKey = mock<PublicKey>()
    private val publicKeyBytes = "public-key".toByteArray()
    private val signatureBytes = "signature".toByteArray()
    private val mockSignatureWithKey = DigitalSignatureWithKey(
        publicKey,
        signatureBytes
    )
    private val mockSignatureSpec = SignatureSpecs.ECDSA_SHA256
    private val keyEncodingService = mock<KeyEncodingService> {
        on { encodeAsByteArray(publicKey) } doReturn publicKeyBytes
    }

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
            groupParametersFactory,
            keyEncodingService,
            clock
        )

        verify(coordinatorFactory).createCoordinator(any(), lifecycleEventCaptor.capture())
    }

    @Test
    fun `persist list of member info before starting component`() {
        val result = membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourSignedMemberInfo))
            .execute()

        assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
    }

    @Test
    fun `persist registration request before starting component`() {
        val result = membershipPersistenceClient.persistRegistrationRequest(
            ourHoldingIdentity,
            ourRegistrationRequest
        ).execute()

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

        whenever(
            memberInfoFactory.createPersistentMemberInfo(
                ourHoldingIdentity.toAvro(),
                ourSignedMemberInfo.memberContextBytes,
                ourSignedMemberInfo.mgmContextBytes,
                ourSignedMemberInfo.memberSignature,
                ourSignedMemberInfo.memberSignatureSpec,
            )
        ).doReturn(ourPersistentMemberInfo)

        membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourSignedMemberInfo))
            .execute()

        with(argumentCaptor<MembershipPersistenceRequest>()) {
            verify(rpcSender).sendRequest(capture())

            assertThat(firstValue.context.requestTimestamp).isBeforeOrEqualTo(clock.instant())
            assertThat(firstValue.context.holdingIdentity)
                .isEqualTo(ourHoldingIdentity.toAvro())

            assertThat(firstValue.request).isInstanceOf(PersistMemberInfo::class.java)
            assertThat((firstValue.request as PersistMemberInfo).signedMembers)
                .isNotEmpty
                .isEqualTo(listOf(ourPersistentMemberInfo))
        }
    }

    @Test
    fun `request to persistence registration request is as expected`() {
        postConfigChangedEvent()
        mockPersistenceResponse()

        membershipPersistenceClient.persistRegistrationRequest(ourHoldingIdentity, ourRegistrationRequest).execute()

        with(argumentCaptor<MembershipPersistenceRequest>()) {
            verify(rpcSender).sendRequest(capture())

            assertThat(firstValue.context.requestTimestamp).isBeforeOrEqualTo(clock.instant())
            assertThat(firstValue.context.holdingIdentity)
                .isEqualTo(ourHoldingIdentity.toAvro())

            assertThat(firstValue.request).isInstanceOf(PersistRegistrationRequest::class.java)
            assertThat((firstValue.request as PersistRegistrationRequest).status)
                .isEqualTo(RegistrationStatus.SENT_TO_MGM)
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

        val result = membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourSignedMemberInfo))
            .execute()

        assertThat(result).isInstanceOf(MembershipPersistenceResult.Success::class.java)
    }

    @Test
    fun `failed response for list of member info is correct`() {
        postConfigChangedEvent()
        mockPersistenceResponse(PersistenceFailedResponse("Placeholder error", ErrorKind.GENERAL), null)

        val result = membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourSignedMemberInfo))
            .execute()

        assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
    }

    @Test
    fun `Mismatch in holding identity between RQ and RS causes failed response`() {
        postConfigChangedEvent()
        mockPersistenceResponse(
            holdingIdentityOverride = net.corda.data.identity.HoldingIdentity("O=BadName,L=London,C=GB", "BAD_ID")
        )
        val result = membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourSignedMemberInfo))
            .execute()

        assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
    }

    @Test
    fun `Mismatch in request timestamp between RQ and RS causes failed response`() {
        postConfigChangedEvent()
        mockPersistenceResponse(
            reqTimestampOverride = clock.instant().plusSeconds(5)
        )

        val result = membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourSignedMemberInfo))
            .execute()

        assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
    }

    @Test
    fun `Mismatch in request ID between RQ and RS causes failed response`() {
        postConfigChangedEvent()
        mockPersistenceResponse(
            reqIdOverride = "Group ID 3"
        )

        val result = membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourSignedMemberInfo))
            .execute()

        assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
    }

    @Test
    fun `Response timestamp over two minutes before request timestamp causes failed response`() {
        postConfigChangedEvent()
        mockPersistenceResponse(
            rsTimestampOverride = clock.instant().minusSeconds(180)
        )

        val result = membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourSignedMemberInfo))
            .execute()
        assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
    }

    @Test
    fun `persistGroupPolicy return success on success`() {
        val groupPolicy = mock<LayeredPropertyMap>()
        postConfigChangedEvent()
        mockPersistenceResponse()

        val result = membershipPersistenceClient.persistGroupPolicy(ourHoldingIdentity, groupPolicy, 1)
            .execute()

        assertThat(result).isEqualTo(MembershipPersistenceResult.success())
    }

    @Test
    fun `persistGroupPolicy returns error in case of failure`() {
        val groupPolicy = mock<LayeredPropertyMap>()
        postConfigChangedEvent()
        mockPersistenceResponse(
            PersistenceFailedResponse("Placeholder error", ErrorKind.GENERAL),
        )

        val result = membershipPersistenceClient.persistGroupPolicy(ourHoldingIdentity, groupPolicy, 1L)
            .execute()

        assertThat(result).isEqualTo(MembershipPersistenceResult.Failure<Unit>("Placeholder error"))
    }

    @Test
    fun `persistGroupPolicy return failure for unexpected result`() {
        val groupPolicy = mock<LayeredPropertyMap>()
        postConfigChangedEvent()
        mockPersistenceResponse(
            1,
        )

        val result = membershipPersistenceClient.persistGroupPolicy(ourHoldingIdentity, groupPolicy, 1L)
            .execute()

        assertThat(result).isEqualTo(MembershipPersistenceResult.Failure<Unit>("Unexpected response: 1"))
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

        membershipPersistenceClient.persistGroupPolicy(ourHoldingIdentity, groupPolicy, 1L)
            .execute()

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
        ).execute()
        assertThat(result).isInstanceOf(MembershipPersistenceResult.Success::class.java)
    }

    @Nested
    inner class PersistGroupParametersInitialSnapshotTests {
        @Test
        fun `persistGroupParametersInitialSnapshot returns the correct epoch`() {
            postConfigChangedEvent()
            val mockAvroGroupParameters = mock<AvroGroupParameters>()
            val mockGroupParameters = mock<SignedGroupParameters>()
            mockPersistenceResponse(
                PersistGroupParametersResponse(mockAvroGroupParameters),
            )
            whenever(groupParametersFactory.create(mockAvroGroupParameters)).doReturn(mockGroupParameters)

            val result = membershipPersistenceClient.persistGroupParametersInitialSnapshot(ourHoldingIdentity)
                .execute()

            assertThat(result).isInstanceOf(MembershipPersistenceResult.Success::class.java)
            assertThat(result.getOrThrow()).isInstanceOf(SignedGroupParameters::class.java)
            assertThat(result).isEqualTo(MembershipPersistenceResult.Success(mockGroupParameters))
        }

        @Test
        fun `persistGroupParametersInitialSnapshot returns error in case of failure`() {
            postConfigChangedEvent()
            mockPersistenceResponse(
                PersistenceFailedResponse("Placeholder error", ErrorKind.GENERAL),
            )

            val result = membershipPersistenceClient.persistGroupParametersInitialSnapshot(ourHoldingIdentity)
                .execute()

            assertThat(result).isEqualTo(
                MembershipPersistenceResult.Failure<SignedGroupParameters>("Placeholder error")
            )
        }

        @Test
        fun `persistGroupParametersInitialSnapshot returns failure for unexpected result`() {
            postConfigChangedEvent()
            mockPersistenceResponse(
                null,
            )

            val result = membershipPersistenceClient.persistGroupParametersInitialSnapshot(ourHoldingIdentity)
                .execute()

            assertThat(result).isEqualTo(MembershipPersistenceResult.Failure<SignedGroupParameters>("Unexpected response: null"))
        }
    }

    @Nested
    inner class PersistGroupParametersTests {
        @Test
        fun `persistGroupParameters returns the correct epoch`() {
            val avroGroupParameters = mock<AvroGroupParameters>()
            val signedGroupParameters = mock<SignedGroupParameters> {
                on { groupParameters } doReturn serialisedParams
                on { mgmSignature } doReturn mockSignatureWithKey
                on { mgmSignatureSpec } doReturn mockSignatureSpec
            }
            postConfigChangedEvent()
            whenever(groupParametersFactory.create(avroGroupParameters)).doReturn(signedGroupParameters)
            mockPersistenceResponse(
                PersistGroupParametersResponse(avroGroupParameters),
            )

            val result = membershipPersistenceClient.persistGroupParameters(ourHoldingIdentity, signedGroupParameters)
                .execute()

            assertThat(result).isEqualTo(MembershipPersistenceResult.Success(signedGroupParameters))
        }

        @Test
        fun `persistGroupParameters returns error in case of failure`() {
            val groupParameters = mock<SignedGroupParameters> {
                on { groupParameters } doReturn serialisedParams
                on { mgmSignature } doReturn mockSignatureWithKey
                on { mgmSignatureSpec } doReturn mockSignatureSpec
            }
            postConfigChangedEvent()
            mockPersistenceResponse(
                PersistenceFailedResponse("Placeholder error", ErrorKind.GENERAL),
            )

            val result = membershipPersistenceClient.persistGroupParameters(ourHoldingIdentity, groupParameters)
                .execute()

            assertThat(result).isEqualTo(MembershipPersistenceResult.Failure<KeyValuePairList>("Placeholder error"))
        }

        @Test
        fun `persistGroupParameters sends the correct data`() {
            val groupParameters = mock<SignedGroupParameters> {
                on { groupParameters } doReturn serialisedParams
                on { mgmSignature } doReturn mockSignatureWithKey
                on { mgmSignatureSpec } doReturn mockSignatureSpec
            }
            postConfigChangedEvent()
            val argument = argumentCaptor<MembershipPersistenceRequest>()
            val response = CompletableFuture.completedFuture(mock<MembershipPersistenceResponse>())
            whenever(rpcSender.sendRequest(argument.capture())).thenReturn(response)

            membershipPersistenceClient.persistGroupParameters(ourHoldingIdentity, groupParameters)
                .execute()

            val sentParams = (argument.firstValue.request as? PersistGroupParameters)?.groupParameters
            assertThat(sentParams)
                .isNotNull

            assertThat(sentParams?.groupParameters)
                .isNotNull
                .isEqualTo(ByteBuffer.wrap(serialisedParams))

            assertThat(sentParams?.mgmSignature)
                .isNotNull

            assertThat(sentParams?.mgmSignature?.publicKey?.array()).isEqualTo(publicKeyBytes)
            assertThat(sentParams?.mgmSignature?.bytes?.array()).isEqualTo(signatureBytes)
            assertThat(sentParams?.mgmSignatureSpec?.signatureName).isEqualTo(mockSignatureSpec.signatureName)
        }
    }

    @Nested
    inner class AddNotaryToGroupParametersTests {
        @Test
        fun `addNotaryToGroupParameters returns the correct epoch`() {
            val notary = ourPersistentMemberInfo
            val mockAvroGroupParameters = mock<AvroGroupParameters>()
            val mockGroupParameters = mock<SignedGroupParameters>()
            postConfigChangedEvent()
            mockPersistenceResponse(
                PersistGroupParametersResponse(mockAvroGroupParameters),
            )
            whenever(groupParametersFactory.create(mockAvroGroupParameters)).doReturn(mockGroupParameters)

            val result = membershipPersistenceClient.addNotaryToGroupParameters(notary)
                .execute()

            assertThat(result).isEqualTo(MembershipPersistenceResult.Success(mockGroupParameters))
        }

        @Test
        fun `addNotaryToGroupParameters returns error in case of failure`() {
            val notary = ourPersistentMemberInfo
            postConfigChangedEvent()
            mockPersistenceResponse(
                PersistenceFailedResponse("Placeholder error", ErrorKind.GENERAL),
            )

            val result = membershipPersistenceClient.addNotaryToGroupParameters(notary)
                .execute()

            assertThat(result).isEqualTo(MembershipPersistenceResult.Failure<KeyValuePairList>("Placeholder error"))
        }

        @Test
        fun `addNotaryToGroupParameters returns failure for unexpected result`() {
            val notary = ourPersistentMemberInfo
            postConfigChangedEvent()
            mockPersistenceResponse(
                null,
            )

            val result = membershipPersistenceClient.addNotaryToGroupParameters(notary)
                .execute()

            assertThat(result).isEqualTo(MembershipPersistenceResult.Failure<KeyValuePairList>("Unexpected response: null"))
        }

        @Test
        fun `addNotaryToGroupParameters sends the correct data`() {
            val notaryInRequest = ourPersistentMemberInfo
            postConfigChangedEvent()
            val argument = argumentCaptor<MembershipPersistenceRequest>()
            val response = CompletableFuture.completedFuture(mock<MembershipPersistenceResponse>())
            whenever(rpcSender.sendRequest(argument.capture())).thenReturn(response)

            membershipPersistenceClient.addNotaryToGroupParameters(notaryInRequest)
                .execute()

            val notary = (argument.firstValue.request as? AddNotaryToGroupParameters)?.notary
            assertThat(notary?.viewOwningMember).isEqualTo(ourHoldingIdentity.toAvro())
            assertThat(notary?.signedMemberContext).isEqualTo(notaryInRequest.signedMemberContext)
            assertThat(notary?.serializedMgmContext).isEqualTo(notaryInRequest.serializedMgmContext)
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
                PersistenceFailedResponse("Placeholder error", ErrorKind.GENERAL),
            )

            val result = membershipPersistenceClient.addApprovalRule(
                ourHoldingIdentity,
                ApprovalRuleParams(RULE_REGEX, ApprovalRuleType.STANDARD, RULE_LABEL)
            )
                .execute()

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
                .execute()

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
                .execute()

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
                .execute()

            assertThat(result).isEqualTo(MembershipPersistenceResult.success())
        }

        @Test
        fun `deleteApprovalRule returns error in case of failure`() {
            postConfigChangedEvent()
            mockPersistenceResponse(
                PersistenceFailedResponse("Placeholder error", ErrorKind.GENERAL),
            )

            val result = membershipPersistenceClient.deleteApprovalRule(
                ourHoldingIdentity,
                RULE_ID,
                PREAUTH
            )
                .execute()

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
                .execute()

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
            val registrationRequestId = "registrationRequestId"
            postConfigChangedEvent()
            mockPersistenceResponse(
                payload = UpdateMemberAndRegistrationRequestResponse(
                    ourPersistentMemberInfo
                )
            )

            val result = membershipPersistenceClient.setMemberAndRegistrationRequestAsApproved(
                ourHoldingIdentity,
                bob,
                registrationRequestId,
            )

            assertThat(result.getOrThrow()).isSameAs(ourPersistentMemberInfo)
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
                .execute()

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
                .execute()

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
                .execute()

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
                .execute()

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        }

        @Test
        fun `mutualTlsAddCertificateToAllowedList return failure after failure`() {
            postConfigChangedEvent()
            mockPersistenceResponse(PersistenceFailedResponse("Placeholder error", ErrorKind.GENERAL))

            val response = membershipPersistenceClient.mutualTlsAddCertificateToAllowedList(
                ourHoldingIdentity,
                ourX500Name.toString(),
            )
                .execute()

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
                .execute()

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
                .execute()

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
                .execute()

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        }

        @Test
        fun `mutualTlsRemoveCertificateFromAllowedList return failure after failure`() {
            postConfigChangedEvent()
            mockPersistenceResponse(PersistenceFailedResponse("Placeholder error", ErrorKind.GENERAL))

            val response = membershipPersistenceClient.mutualTlsRemoveCertificateFromAllowedList(
                ourHoldingIdentity,
                ourX500Name.toString(),
            )
                .execute()

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
                .execute()

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }
    }

    @Nested
    inner class PreAuthTokenTests {
        private val uuid = UUID.randomUUID()
        private val ttl = Instant.ofEpochSecond(100)
        private val remarks = "a remark"
        private val removalRemark = "another remark"

        @BeforeEach
        fun setUp() = postConfigChangedEvent()

        @Test
        fun `generatePreAuthToken sends the correct request`() {
            val argument = argumentCaptor<MembershipPersistenceRequest>()
            val response = CompletableFuture.completedFuture(mock<MembershipPersistenceResponse>())
            whenever(rpcSender.sendRequest(argument.capture())).thenReturn(response)

            membershipPersistenceClient.generatePreAuthToken(ourHoldingIdentity, uuid, ourX500Name, ttl, remarks)
                .execute()

            assertThat(argument.firstValue.request).isInstanceOf(AddPreAuthToken::class.java)
            val request = (argument.firstValue.request as AddPreAuthToken)
            assertThat(request.tokenId).isEqualTo(uuid.toString())
            assertThat(request.remark).isEqualTo(remarks)
            assertThat(request.ttl).isEqualTo(ttl)
            assertThat(request.ownerX500Name).isEqualTo(ourX500Name.toString())
        }

        @Test
        fun `generatePreAuthToken returns the token correctly`() {
            mockPersistenceResponse()

            val response =
                membershipPersistenceClient.generatePreAuthToken(ourHoldingIdentity, uuid, ourX500Name, ttl, remarks)
                    .execute()

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        }

        @Test
        fun `generatePreAuthToken return failure after failure`() {
            mockPersistenceResponse(PersistenceFailedResponse("Placeholder error", ErrorKind.GENERAL))

            val response =
                membershipPersistenceClient.generatePreAuthToken(ourHoldingIdentity, uuid, ourX500Name, ttl, remarks)
                    .execute()

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }

        @Test
        fun `generatePreAuthToken return failure after unknown result`() {
            mockPersistenceResponse("Placeholder error")

            val response =
                membershipPersistenceClient.generatePreAuthToken(ourHoldingIdentity, uuid, ourX500Name, ttl, remarks)
                    .execute()

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }

        @Test
        fun `revokePreAuthToken sends the correct request`() {
            val argument = argumentCaptor<MembershipPersistenceRequest>()
            val response = CompletableFuture.completedFuture(mock<MembershipPersistenceResponse>())
            whenever(rpcSender.sendRequest(argument.capture())).thenReturn(response)

            membershipPersistenceClient.revokePreAuthToken(ourHoldingIdentity, uuid, removalRemark)
                .execute()

            assertThat(argument.firstValue.request).isInstanceOf(RevokePreAuthToken::class.java)
            val request = (argument.firstValue.request as RevokePreAuthToken)
            assertThat(request.tokenId).isEqualTo(uuid.toString())
            assertThat(request.remark).isEqualTo(removalRemark)
        }

        @Test
        fun `revokePreAuthToken returns the token correctly`() {
            val mockToken = mock<PreAuthToken>()
            mockPersistenceResponse(RevokePreAuthTokenResponse(mockToken))

            val response =
                membershipPersistenceClient.revokePreAuthToken(ourHoldingIdentity, uuid, removalRemark).getOrThrow()

            assertThat(response).isEqualTo(mockToken)
        }

        @Test
        fun `revokePreAuthToken return failure after failure`() {
            mockPersistenceResponse(PersistenceFailedResponse("Placeholder error", ErrorKind.GENERAL))

            val response = membershipPersistenceClient.revokePreAuthToken(ourHoldingIdentity, uuid, removalRemark)
                .execute()

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }

        @Test
        fun `revokePreAuthToken return failure after unknown result`() {
            mockPersistenceResponse("Placeholder error")

            val response = membershipPersistenceClient.revokePreAuthToken(ourHoldingIdentity, uuid, removalRemark)
                .execute()

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }

        @Test
        fun `consumePreAuthToken persistence request is built as expected`() {
            mockPersistenceResponse()

            membershipPersistenceClient.consumePreAuthToken(
                ourHoldingIdentity,
                bobX500Name,
                uuid
            )
                .execute()

            verify(rpcSender).sendRequest(
                argThat {
                    request is ConsumePreAuthToken &&
                        (request as ConsumePreAuthToken).tokenId == uuid.toString() &&
                        (request as ConsumePreAuthToken).ownerX500Name == bobX500Name.toString() &&
                        context.holdingIdentity == ourHoldingIdentity.toAvro()
                }
            )
        }

        @Test
        fun `consumePreAuthToken returns success if persistence operation was successful`() {
            mockPersistenceResponse()

            val response = membershipPersistenceClient.consumePreAuthToken(
                ourHoldingIdentity,
                bobX500Name,
                uuid
            )
                .execute()

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        }

        @Test
        fun `consumePreAuthToken returns failure after failure`() {
            mockPersistenceResponse(PersistenceFailedResponse("Placeholder error", ErrorKind.GENERAL))

            val response = membershipPersistenceClient.consumePreAuthToken(
                ourHoldingIdentity,
                bobX500Name,
                uuid
            )
                .execute()

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }

        @Test
        fun `consumePreAuthToken returns failure after unknown result`() {
            mockPersistenceResponse("Placeholder error")

            val response = membershipPersistenceClient.consumePreAuthToken(
                ourHoldingIdentity,
                bobX500Name,
                uuid
            )
                .execute()

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }
    }

    @Nested
    inner class SuspendMemberTests {
        @BeforeEach
        fun setUp() = postConfigChangedEvent()

        @Test
        fun `suspendMember returns the correct result when group parameters not updated`() {
            mockPersistenceResponse(SuspendMemberResponse(mock(), null))

            val result = membershipPersistenceClient.suspendMember(
                ourHoldingIdentity,
                bobX500Name,
                null,
                null
            ).execute()
            verify(groupParametersFactory, never()).create(any<AvroGroupParameters>())

            assertThat(result).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        }

        @Test
        fun `suspendMember returns the correct result when group parameters updated`() {
            val groupParameters = mock<AvroGroupParameters>()
            mockPersistenceResponse(SuspendMemberResponse(mock(), groupParameters))

            val result = membershipPersistenceClient.suspendMember(
                ourHoldingIdentity,
                bobX500Name,
                null,
                null
            ).execute()
            verify(groupParametersFactory).create(groupParameters)

            assertThat(result).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        }

        @Test
        fun `suspendMember returns error in case of failure`() {
            mockPersistenceResponse(
                PersistenceFailedResponse("Placeholder error", ErrorKind.GENERAL),
            )

            val result = membershipPersistenceClient.suspendMember(
                ourHoldingIdentity,
                bobX500Name,
                null,
                null
            ).execute()

            assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }

        @Test
        fun `suspendMember returns failure after unknown result`() {
            mockPersistenceResponse("Placeholder error")

            val response = membershipPersistenceClient.suspendMember(
                ourHoldingIdentity,
                bobX500Name,
                null,
                null
            ).execute()

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }

        @Test
        fun `suspendMember sends the correct data`() {
            val argument = argumentCaptor<MembershipPersistenceRequest>()
            val response = CompletableFuture.completedFuture(mock<MembershipPersistenceResponse>())
            whenever(rpcSender.sendRequest(argument.capture())).thenReturn(response)

            membershipPersistenceClient.suspendMember(
                ourHoldingIdentity,
                bobX500Name,
                SERIAL,
                REASON
            ).execute()

            val sentRequest = (argument.firstValue.request as? SuspendMember)!!
            assertThat(sentRequest.suspendedMember).isEqualTo(bobX500Name.toString())
            assertThat(sentRequest.serialNumber).isEqualTo(SERIAL)
            assertThat(sentRequest.reason).isEqualTo(REASON)
        }
    }

    @Nested
    inner class ActivateMemberTests {
        @BeforeEach
        fun setUp() = postConfigChangedEvent()

        @Test
        fun `activateMember returns the correct result when group parameters not updated`() {
            mockPersistenceResponse(ActivateMemberResponse(mock(), null))

            val result = membershipPersistenceClient.activateMember(
                ourHoldingIdentity,
                bobX500Name,
                null,
                null
            ).execute()
            verify(groupParametersFactory, never()).create(any<AvroGroupParameters>())

            assertThat(result).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        }

        @Test
        fun `activateMember returns the correct result when group parameters updated`() {
            val groupParameters = mock<AvroGroupParameters>()
            mockPersistenceResponse(ActivateMemberResponse(mock(), groupParameters))

            val result = membershipPersistenceClient.activateMember(
                ourHoldingIdentity,
                bobX500Name,
                null,
                null
            ).execute()
            verify(groupParametersFactory).create(groupParameters)

            assertThat(result).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        }

        @Test
        fun `activateMember returns error in case of failure`() {
            mockPersistenceResponse(
                PersistenceFailedResponse("Placeholder error", ErrorKind.GENERAL),
            )

            val result = membershipPersistenceClient.activateMember(
                ourHoldingIdentity,
                bobX500Name,
                null,
                null
            ).execute()

            assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }

        @Test
        fun `activateMember returns failure after unknown result`() {
            mockPersistenceResponse("Placeholder error")

            val response = membershipPersistenceClient.activateMember(
                ourHoldingIdentity,
                bobX500Name,
                null,
                null
            ).execute()

            assertThat(response).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }

        @Test
        fun `activateMember sends the correct data`() {
            val argument = argumentCaptor<MembershipPersistenceRequest>()
            val response = CompletableFuture.completedFuture(mock<MembershipPersistenceResponse>())
            whenever(rpcSender.sendRequest(argument.capture())).thenReturn(response)

            membershipPersistenceClient.activateMember(
                ourHoldingIdentity,
                bobX500Name,
                SERIAL,
                REASON
            ).execute()

            val sentRequest = (argument.firstValue.request as? ActivateMember)!!
            assertThat(sentRequest.activatedMember).isEqualTo(bobX500Name.toString())
            assertThat(sentRequest.serialNumber).isEqualTo(SERIAL)
            assertThat(sentRequest.reason).isEqualTo(REASON)
        }
    }

    @Nested
    inner class UpdateStaticNetworkInfoTest {

        private val groupId = UUID(0, 1).toString()
        private val groupParameters = KeyValuePairList(emptyList())
        private val mgmPublicSigningKey = ByteBuffer.wrap("123".toByteArray())
        private val mgmPrivateSigningKey = ByteBuffer.wrap("456".toByteArray())
        private val version = 1

        private val info = StaticNetworkInfo(
            groupId,
            groupParameters,
            mgmPublicSigningKey,
            mgmPrivateSigningKey,
            version
        )

        @Test
        fun `Assert request and response are as expected when persisting`() {
            val queryResponse = StaticNetworkInfoQueryResponse(info)

            postConfigChangedEvent()
            mockPersistenceResponse(queryResponse)

            val output = membershipPersistenceClient.updateStaticNetworkInfo(info).execute()

            val argument = argumentCaptor<MembershipPersistenceRequest>()
            verify(rpcSender).sendRequest(argument.capture())

            val persistenceRequest = argument.firstValue as? MembershipPersistenceRequest
            assertThat(persistenceRequest).isNotNull
            assertThat(persistenceRequest!!.context.holdingIdentity).isNull()

            val sentRequest = (persistenceRequest.request as? UpdateStaticNetworkInfo)!!
            assertThat(sentRequest.info).isEqualTo(info)

            assertThat(output).isInstanceOf(MembershipPersistenceResult.Success::class.java)
            assertThat(output.getOrThrow()).isEqualTo(info)
        }

        @Test
        fun `Assert persistence result is failure if persistence failed`() {
            val error = "foo-bar"

            postConfigChangedEvent()
            mockPersistenceResponse(PersistenceFailedResponse(error, ErrorKind.GENERAL))

            val output = membershipPersistenceClient.updateStaticNetworkInfo(info)
                .execute()

            assertThat(output).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
            assertThat((output as MembershipPersistenceResult.Failure).errorMsg).isEqualTo(error)
        }

        @Test
        fun `Assert persistence result is failure if persistence response is unexpected`() {
            class BadResponse

            postConfigChangedEvent()
            mockPersistenceResponse(BadResponse())

            val output = membershipPersistenceClient.updateStaticNetworkInfo(info)
                .execute()

            assertThat(output).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
            assertThat((output as MembershipPersistenceResult.Failure).errorMsg).contains("Unexpected response")
        }
    }

    @Nested
    inner class UpdateGroupParametersTest {
        @Test
        fun `Assert request and response are as expected when persisting`() {
            val update = mapOf("ext.key" to "value")
            val mockAvroParameters = mock<AvroGroupParameters>()
            val queryResponse = PersistGroupParametersResponse(mockAvroParameters)
            val mockParameters = mock<SignedGroupParameters>()
            whenever(groupParametersFactory.create(mockAvroParameters)).doReturn(mockParameters)

            postConfigChangedEvent()
            mockPersistenceResponse(queryResponse)

            val output = membershipPersistenceClient.updateGroupParameters(
                ourHoldingIdentity,
                update
            ).execute()

            val argument = argumentCaptor<MembershipPersistenceRequest>()
            verify(rpcSender).sendRequest(argument.capture())

            val persistenceRequest = argument.firstValue as? MembershipPersistenceRequest
            assertThat(persistenceRequest).isNotNull
            assertThat(persistenceRequest!!.context.holdingIdentity).isEqualTo(ourHoldingIdentity.toAvro())

            val sentRequest = (persistenceRequest.request as? UpdateGroupParameters)!!
            assertThat(sentRequest.update).isEqualTo(update)

            assertThat(output).isInstanceOf(MembershipPersistenceResult.Success::class.java)
            assertThat(output.getOrThrow()).isEqualTo(mockParameters)
        }

        @Test
        fun `Assert persistence result is failure if persistence failed`() {
            val error = "foo-bar"

            postConfigChangedEvent()
            mockPersistenceResponse(PersistenceFailedResponse(error, ErrorKind.GENERAL))

            val output = membershipPersistenceClient.updateGroupParameters(ourHoldingIdentity, emptyMap()).execute()

            assertThat(output).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
            assertThat((output as MembershipPersistenceResult.Failure).errorMsg).isEqualTo(error)
        }

        @Test
        fun `Assert persistence result is failure if persistence response is unexpected`() {
            class BadResponse

            postConfigChangedEvent()
            mockPersistenceResponse(BadResponse())

            val output = membershipPersistenceClient.updateGroupParameters(ourHoldingIdentity, emptyMap()).execute()

            assertThat(output).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
            assertThat((output as MembershipPersistenceResult.Failure).errorMsg).contains("Unexpected response")
        }
    }

    @Nested
    inner class PersistHostedIdentityTests {
        private val tlsCertAlias = "tls"
        private val sessionKeyId = "123412341234"
        private val sessionCertAlias = "session"

        @Test
        fun `persistHostedIdentity returns the correct result`() {
            postConfigChangedEvent()
            val expectedResult = PersistHostedIdentityResponse(5)
            mockPersistenceResponse(payload = expectedResult)

            val result = membershipPersistenceClient.persistHostedIdentity(
                ourHoldingIdentity,
                tlsCertAlias,
                true,
                SessionKeyAndCertificate(sessionKeyId, sessionCertAlias, true),
                emptyList()
            )

            assertThat(result.getOrThrow()).isEqualTo(expectedResult.version)
        }

        @Test
        fun `persistHostedIdentity returns error in case of failure`() {
            postConfigChangedEvent()
            mockPersistenceResponse(
                PersistenceFailedResponse("Placeholder error", ErrorKind.GENERAL),
            )

            val result = membershipPersistenceClient.persistHostedIdentity(
                ourHoldingIdentity,
                tlsCertAlias,
                true,
                SessionKeyAndCertificate(sessionKeyId, sessionCertAlias, true),
                emptyList()
            ).execute()

            assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }

        @Test
        fun `persistHostedIdentity returns failure for unexpected result`() {
            postConfigChangedEvent()
            mockPersistenceResponse(
                null,
            )

            val result = membershipPersistenceClient.persistHostedIdentity(
                ourHoldingIdentity,
                tlsCertAlias,
                true,
                SessionKeyAndCertificate(sessionKeyId, sessionCertAlias, true),
                emptyList()
            ).execute()

            assertThat(result).isEqualTo(MembershipPersistenceResult.Failure<ApprovalRuleDetails>("Unexpected response: null"))
        }

        @Test
        fun `persistHostedIdentity sends the correct data`() {
            val sessionKeyAndCert = SessionKeyAndCertificate(sessionKeyId, sessionCertAlias, true)
            postConfigChangedEvent()
            val argument = argumentCaptor<MembershipPersistenceRequest>()
            val response = CompletableFuture.completedFuture(mock<MembershipPersistenceResponse>())
            whenever(rpcSender.sendRequest(argument.capture())).thenReturn(response)

            membershipPersistenceClient.persistHostedIdentity(
                ourHoldingIdentity,
                tlsCertAlias,
                true,
                sessionKeyAndCert,
                emptyList()
            ).execute()

            val sentRequest = (argument.firstValue.request as? PersistHostedIdentity)!!
            assertThat(sentRequest.tlsCertificateAlias).isEqualTo(tlsCertAlias)
            assertThat(sentRequest.useClusterLevelTls).isTrue()
            assertThat(sentRequest.sessionKeysAndCertificates).isEqualTo(listOf(sessionKeyAndCert))
        }
    }
}
