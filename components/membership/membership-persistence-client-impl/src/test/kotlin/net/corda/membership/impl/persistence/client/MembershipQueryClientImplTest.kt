package net.corda.membership.impl.persistence.client

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.query.QueryMemberInfo
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.db.response.MembershipResponseContext
import net.corda.data.membership.db.response.query.MemberInfoQueryResponse
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
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas.Membership.Companion.MEMBERSHIP_DB_RPC_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture

class MembershipQueryClientImplTest {

    lateinit var membershipQueryClient: MembershipQueryClient

    private val ourX500Name = MemberX500Name.parse("O=Alice,L=London,C=GB")
    private val ourGroupId = UUID.randomUUID().toString()
    private val ourHoldingIdentity = HoldingIdentity(ourX500Name.toString(), ourGroupId)
    private val ourMemberInfo: MemberInfo = mock()
    private val registrationId = UUID.randomUUID().toString()

    private val lifecycleEventCaptor = argumentCaptor<LifecycleEventHandler>()

    private val expectedConfigs = setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG)

    private val clock = UTCClock()

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
    private val memberInfoFactory: MemberInfoFactory = mock {
        on { create(any()) } doReturn ourMemberInfo
    }

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
            ), coordinator
        )
    }

    @BeforeEach
    fun setUp() {
        membershipQueryClient = MembershipQueryClientImpl(
            coordinatorFactory, publisherFactory, configurationReadService, memberInfoFactory
        )

        verify(coordinatorFactory).createCoordinator(any(), lifecycleEventCaptor.capture())
    }

    @Test
    fun `query all member infos before starting component`() {
        val result = membershipQueryClient.queryMemberInfo(ourHoldingIdentity)

        assertThat(result.success).isFalse
        assertThat(result.payload).isNull()
        assertThat(result.errorMsg).isNotBlank
    }

    @Test
    fun `query specific member info before starting component`() {
        val result = membershipQueryClient.queryMemberInfo(ourHoldingIdentity, listOf(ourHoldingIdentity))

        assertThat(result.success).isFalse
        assertThat(result.payload).isNull()
        assertThat(result.errorMsg).isNotBlank
    }

    @Test
    fun `query registration request before starting component`() {
        // Function is not yet implemented
        assertThrows<UnsupportedOperationException> {
            membershipQueryClient.queryRegistrationRequest(ourHoldingIdentity, registrationId)
        }
    }

    @Test
    fun `starting component starts coordinator`() {
        membershipQueryClient.start()
        verify(coordinator).start()
    }

    @Test
    fun `stopping component stops coordinator`() {
        membershipQueryClient.stop()
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

    fun buildResponse(
        rsContext: MembershipResponseContext,
        success: Boolean,
        payload: Any?
    ) = MembershipPersistenceResponse(
        rsContext,
        success,
        payload,
        null
    )

    @Suppress("LongParameterList")
    fun mockPersistenceResponse(
        success: Boolean,
        payload: List<PersistentMemberInfo>?,
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
                    success,
                    payload?.let { MemberInfoQueryResponse(it) }
                )
            )
        }
    }

    @Test
    fun `request to persistence service is as expected`() {
        postConfigChangedEvent()
        mockPersistenceResponse(true, listOf(mock()))

        membershipQueryClient.queryMemberInfo(ourHoldingIdentity)

        with(argumentCaptor<MembershipPersistenceRequest>()) {
            verify(rpcSender).sendRequest(capture())

            assertThat(firstValue.context.requestTimestamp).isBeforeOrEqualTo(clock.instant())
            assertThat(firstValue.context.holdingIdentity)
                .isEqualTo(ourHoldingIdentity.toAvro())

            assertThat(firstValue.request).isInstanceOf(QueryMemberInfo::class.java)
            assertThat((firstValue.request as QueryMemberInfo).queryIdentities).isEmpty()
        }

    }

    @Test
    fun `successful request for all member info is correct`() {
        postConfigChangedEvent()
        mockPersistenceResponse(true, listOf(mock()))

        with(membershipQueryClient.queryMemberInfo(ourHoldingIdentity)) {
            assertThat(success).isTrue
            assertThat(payload).hasSize(1)
        }
    }

    @Test
    fun `failed request for all member info is correct`() {
        postConfigChangedEvent()
        mockPersistenceResponse(false, null)

        with(membershipQueryClient.queryMemberInfo(ourHoldingIdentity)) {
            assertThat(success).isFalse
            assertThat(payload).isNull()
            assertThat(errorMsg).isNotNull
        }
    }

    @Test
    fun `successful request for list of member info is correct`() {
        postConfigChangedEvent()
        mockPersistenceResponse(true, listOf(mock()))

        with(membershipQueryClient.queryMemberInfo(ourHoldingIdentity, listOf(ourHoldingIdentity))) {
            assertThat(success).isTrue
            assertThat(payload).hasSize(1)
        }
    }

    @Test
    fun `failed request for list of member info is correct`() {
        postConfigChangedEvent()
        mockPersistenceResponse(false, null)

        with(membershipQueryClient.queryMemberInfo(ourHoldingIdentity, listOf(ourHoldingIdentity))) {
            assertThat(success).isFalse
            assertThat(payload).isNull()
            assertThat(errorMsg).isNotNull
        }
    }

    @Test
    fun `successful request for member info with no results is correct`() {
        postConfigChangedEvent()
        mockPersistenceResponse(true, emptyList())

        with(membershipQueryClient.queryMemberInfo(ourHoldingIdentity)) {
            assertThat(success).isTrue
            assertThat(payload).isEmpty()
        }
    }

    @Test
    fun `Mismatch in holding identity between RQ and RS causes failed response`() {
        postConfigChangedEvent()
        mockPersistenceResponse(
            true,
            emptyList(),
            holdingIdentityOverride = net.corda.data.identity.HoldingIdentity("O=BadName,L=London,C=GB", "BAD_ID")
        )
        with(membershipQueryClient.queryMemberInfo(ourHoldingIdentity, listOf(ourHoldingIdentity))) {
            assertThat(success).isFalse
            assertThat(payload).isNull()
            assertThat(errorMsg).isNotNull
        }
    }

    @Test
    fun `Mismatch in request timestamp between RQ and RS causes failed response`() {
        postConfigChangedEvent()
        mockPersistenceResponse(
            true,
            emptyList(),
            reqTimestampOverride = clock.instant().plusSeconds(5)
        )
        with(membershipQueryClient.queryMemberInfo(ourHoldingIdentity, listOf(ourHoldingIdentity))) {
            assertThat(success).isFalse
            assertThat(payload).isNull()
            assertThat(errorMsg).isNotNull
        }
    }

    @Test
    fun `Mismatch in request ID between RQ and RS causes failed response`() {
        postConfigChangedEvent()
        mockPersistenceResponse(
            true,
            emptyList(),
            reqIdOverride = UUID.randomUUID().toString()
        )
        with(membershipQueryClient.queryMemberInfo(ourHoldingIdentity, listOf(ourHoldingIdentity))) {
            assertThat(success).isFalse
            assertThat(payload).isNull()
            assertThat(errorMsg).isNotNull
        }
    }

    @Test
    fun `Response timestamp before request timestamp causes failed response`() {
        postConfigChangedEvent()
        mockPersistenceResponse(
            true,
            emptyList(),
            rsTimestampOverride = clock.instant().minusSeconds(10)
        )
        with(membershipQueryClient.queryMemberInfo(ourHoldingIdentity, listOf(ourHoldingIdentity))) {
            assertThat(success).isFalse
            assertThat(payload).isNull()
            assertThat(errorMsg).isNotNull
        }
    }
}