package net.corda.membership.impl.persistence.client

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.command.PersistGroupPolicy
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.data.membership.db.request.command.RegistrationStatus
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.db.response.MembershipResponseContext
import net.corda.data.membership.db.response.command.PersistGroupPolicyResponse
import net.corda.data.membership.db.response.query.PersistenceFailedResponse
import net.corda.data.membership.db.response.query.UpdateMemberAndRegistrationRequestResponse
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas.Membership.Companion.MEMBERSHIP_DB_RPC_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
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
import java.util.concurrent.CompletableFuture

class MembershipPersistenceClientImplTest {

    lateinit var membershipPersistenceClient: MembershipPersistenceClient

    private val lifecycleEventCaptor = argumentCaptor<LifecycleEventHandler>()

    private val expectedConfigs = setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG)

    private val clock = TestClock(Instant.ofEpochSecond(0))

    private val registrationHandle: RegistrationHandle = mock()
    private val configHandle: AutoCloseable = mock()
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
    private val ourHoldingIdentity = HoldingIdentity(ourX500Name.toString(), ourGroupId)

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
        ByteBuffer.wrap("456".toByteArray()),
        ByteBuffer.wrap("789".toByteArray()),
    )

    private val memberInfoFactory = mock<MemberInfoFactory>()

    private val testConfig =
        SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.parseString("instanceId=1"))

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
            coordinatorFactory, publisherFactory, configurationReadService, memberInfoFactory, clock
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
    inner class SetMemberAndRegistrationRequestAsApprovedTests {
        @Test
        fun `it returns the correct member info`() {
            val bob = HoldingIdentity("O=Bob ,L=London, C=GB", ourGroupId)
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
            val bob = HoldingIdentity("O=Bob ,L=London, C=GB", ourGroupId)
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
            val bob = HoldingIdentity("O=Bob ,L=London, C=GB", ourGroupId)
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
}
