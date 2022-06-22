package net.corda.membership.impl.persistence.client

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.data.membership.db.request.command.RegistrationStatus
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.db.response.MembershipResponseContext
import net.corda.data.membership.db.response.query.QueryFailedResponse
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas.Membership.Companion.MEMBERSHIP_DB_RPC_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
import java.util.*
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
    private val ourGroupId = UUID.randomUUID().toString()
    private val ourHoldingIdentity = HoldingIdentity(ourX500Name.toString(), ourGroupId)

    private val memberProvidedContext: MemberContext = mock()
    private val mgmProvidedContext: MGMContext = mock()
    private val ourMemberInfo: MemberInfo = mock {
        on { memberProvidedContext } doReturn memberProvidedContext
        on { mgmProvidedContext } doReturn mgmProvidedContext
    }
    private val registrationId = UUID.randomUUID().toString()
    private val ourRegistrationRequest = RegistrationRequest(
        registrationId,
        ourHoldingIdentity,
        ByteBuffer.wrap("123".toByteArray()),
        ByteBuffer.wrap("456".toByteArray()),
        ByteBuffer.wrap("789".toByteArray()),
    )

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
        membershipPersistenceClient = MembershipPersistenceClientImpl(
            coordinatorFactory, publisherFactory, configurationReadService
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

    fun buildResponse(
        rsContext: MembershipResponseContext,
        success: Boolean
    ) = MembershipPersistenceResponse(
        rsContext,
        if (success) null else QueryFailedResponse("Placeholder error")
    )

    fun mockPersistenceResponse(
        success: Boolean,
        reqTimestampOverride: Instant? = null,
        reqIdOverride: String? = null,
        rsTimestampOverride: Instant? = null,
        holdingIdentityOverride: net.corda.data.identity.HoldingIdentity? = null,
    ) {
        whenever(rpcSender.sendRequest(any())).thenAnswer {
            clock.setTime(Instant.now().plusMillis(1))
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
                    success
                )
            )
        }
    }

    @Test
    fun `request to persistence list of member infos is as expected`() {
        postConfigChangedEvent()
        mockPersistenceResponse(true)

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
        mockPersistenceResponse(true)

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
        mockPersistenceResponse(true)

        val result = membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourMemberInfo))
        assertThat(result).isInstanceOf(MembershipPersistenceResult.Success::class.java)
    }

    @Test
    fun `failed response for list of member info is correct`() {
        postConfigChangedEvent()
        mockPersistenceResponse(false, null)

        val result = membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourMemberInfo))
        assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
    }

    @Test
    fun `Mismatch in holding identity between RQ and RS causes failed response`() {
        postConfigChangedEvent()
        mockPersistenceResponse(
            true,
            holdingIdentityOverride = net.corda.data.identity.HoldingIdentity("O=BadName,L=London,C=GB", "BAD_ID")
        )
        val result = membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourMemberInfo))
        assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
    }

    @Test
    fun `Mismatch in request timestamp between RQ and RS causes failed response`() {
        postConfigChangedEvent()
        mockPersistenceResponse(
            true,
            reqTimestampOverride = clock.instant().plusSeconds(5)
        )
        val result = membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourMemberInfo))
        assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
    }

    @Test
    fun `Mismatch in request ID between RQ and RS causes failed response`() {
        postConfigChangedEvent()
        mockPersistenceResponse(
            true,
            reqIdOverride = UUID.randomUUID().toString()
        )
        val result = membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourMemberInfo))
        assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
    }

    @Test
    fun `Response timestamp before request timestamp causes failed response`() {
        postConfigChangedEvent()
        mockPersistenceResponse(
            true,
            rsTimestampOverride = clock.instant().minusSeconds(10)
        )
        val result = membershipPersistenceClient.persistMemberInfo(ourHoldingIdentity, listOf(ourMemberInfo))
        assertThat(result).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
    }
}