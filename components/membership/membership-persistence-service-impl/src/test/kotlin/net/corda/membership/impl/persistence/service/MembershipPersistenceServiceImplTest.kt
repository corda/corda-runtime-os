package net.corda.membership.impl.persistence.service

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.platform.PlatformInfoProvider
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
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.mtls.allowed.list.service.AllowedCertificatesReaderWriterService
import net.corda.membership.persistence.service.MembershipPersistenceService
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.schema.Schemas.Membership.Companion.MEMBERSHIP_DB_RPC_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class MembershipPersistenceServiceImplTest {

    private lateinit var membershipPersistenceService: MembershipPersistenceService

    private val subscriptionCoordinatorName = LifecycleCoordinatorName("SUB")
    private val rpcSubscription: RPCSubscription<MembershipPersistenceRequest, MembershipPersistenceResponse> = mock {
        on { subscriptionName } doReturn subscriptionCoordinatorName
    }
    private val subRegistrationHandle: RegistrationHandle = mock()
    private val registrationHandle: RegistrationHandle = mock()
    private val configHandle: Resource = mock()

    private val testConfig =
        SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.parseString("instanceId=1"))

    private val dependentComponents = setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
        LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
        LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
        LifecycleCoordinatorName.forComponent<AllowedCertificatesReaderWriterService>(),
    )
    private val lifecycleHandlerCaptor: KArgumentCaptor<LifecycleEventHandler> = argumentCaptor()

    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(eq(dependentComponents)) } doReturn registrationHandle
        on { followStatusChangesByName(eq(setOf(subscriptionCoordinatorName))) } doReturn subRegistrationHandle
    }
    private val coordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), lifecycleHandlerCaptor.capture()) } doReturn coordinator
    }

    private val subscriptionFactory: SubscriptionFactory = mock {
        on {
            createRPCSubscription(
                any<RPCConfig<MembershipPersistenceRequest, MembershipPersistenceResponse>>(),
                any(),
                any()
            )
        } doReturn rpcSubscription
    }
    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(eq(coordinator), any()) } doReturn configHandle
    }
    private val dbConnectionManager: DbConnectionManager = mock()
    private val jpaEntitiesRegistry: JpaEntitiesRegistry = mock()
    private val memberInfoFactory: MemberInfoFactory = mock()
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock()
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock()
    private val keyEncodingService: KeyEncodingService = mock()
    private val platformInfoProvider: PlatformInfoProvider = mock()

    @BeforeEach
    fun setUp() {
        membershipPersistenceService = MembershipPersistenceServiceImpl(
            coordinatorFactory,
            subscriptionFactory,
            configurationReadService,
            dbConnectionManager,
            jpaEntitiesRegistry,
            memberInfoFactory,
            cordaAvroSerializationFactory,
            virtualNodeInfoReadService,
            keyEncodingService,
            platformInfoProvider,
            mock()
        )
        verify(coordinatorFactory).createCoordinator(any(), any())
    }

    @Test
    fun `start starts the coordinator`() {
        membershipPersistenceService.start()
        verify(coordinator).start()
    }

    @Test
    fun `stop stops the coordinator`() {
        membershipPersistenceService.stop()
        verify(coordinator).stop()
    }

    fun postStartEvent() {
        lifecycleHandlerCaptor.firstValue.processEvent(StartEvent(), coordinator)
    }

    fun postStopEvent() {
        lifecycleHandlerCaptor.firstValue.processEvent(StopEvent(), coordinator)
    }

    fun postRegistrationStatusChangeEvent(
        status: LifecycleStatus,
        handle: RegistrationHandle = registrationHandle
    ) {
        lifecycleHandlerCaptor.firstValue.processEvent(
            RegistrationStatusChangeEvent(
                handle,
                status
            ),
            coordinator
        )
    }

    private fun postConfigChangedEvent() {
        lifecycleHandlerCaptor.firstValue.processEvent(
            ConfigChangedEvent(
                setOf(BOOT_CONFIG, MESSAGING_CONFIG),
                mapOf(
                    BOOT_CONFIG to testConfig,
                    MESSAGING_CONFIG to testConfig
                )
            ), coordinator
        )
    }

    @Test
    fun `registration handle created on start and closed on stop`() {
        postStartEvent()

        verify(registrationHandle, never()).close()
        verify(coordinator).followStatusChangesByName(eq(dependentComponents))

        postStartEvent()

        verify(registrationHandle).close()
        verify(coordinator, times(2)).followStatusChangesByName(eq(dependentComponents))

        postStopEvent()
        verify(registrationHandle, times(2)).close()
    }

    @Test
    fun `status set to down after stop`() {
        postStopEvent()

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        verify(registrationHandle, never()).close()
        verify(configHandle, never()).close()
        verify(rpcSubscription, never()).close()
    }

    @Test
    fun `registration status UP create config handle and closes it first if it exists`() {
        postStartEvent()
        postRegistrationStatusChangeEvent(LifecycleStatus.UP)

        val configArgs = argumentCaptor<Set<String>>()
        verify(configHandle, never()).close()
        verify(configurationReadService).registerComponentForUpdates(
            eq(coordinator),
            configArgs.capture()
        )
        assertThat(configArgs.firstValue).isEqualTo(setOf(BOOT_CONFIG, MESSAGING_CONFIG))

        postRegistrationStatusChangeEvent(LifecycleStatus.UP)
        verify(configHandle).close()
        verify(configurationReadService, times(2)).registerComponentForUpdates(eq(coordinator), any())

        postStopEvent()
        verify(configHandle, times(2)).close()
    }

    @Test
    fun `registration status DOWN sets status to DOWN`() {
        postRegistrationStatusChangeEvent(LifecycleStatus.DOWN)

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
    }

    @Test
    fun `registration status ERROR sets status to DOWN`() {
        postRegistrationStatusChangeEvent(LifecycleStatus.ERROR)

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
    }

    @Test
    fun `config changed event creates subscription`() {
        postConfigChangedEvent()

        val configCaptor = argumentCaptor<RPCConfig<MembershipPersistenceRequest, MembershipPersistenceResponse>>()
        verify(rpcSubscription, never()).close()
        verify(subscriptionFactory).createRPCSubscription(
            configCaptor.capture(),
            any(),
            any()
        )
        verify(rpcSubscription).start()
        verify(rpcSubscription).subscriptionName
        verify(coordinator).followStatusChangesByName(eq(setOf(rpcSubscription.subscriptionName)))

        with(configCaptor.firstValue) {
            assertThat(requestTopic).isEqualTo(MEMBERSHIP_DB_RPC_TOPIC)
            assertThat(requestType).isEqualTo(MembershipPersistenceRequest::class.java)
            assertThat(responseType).isEqualTo(MembershipPersistenceResponse::class.java)
        }

        postConfigChangedEvent()
        verify(rpcSubscription).close()
        verify(subscriptionFactory, times(2)).createRPCSubscription(
            configCaptor.capture(),
            any(),
            any()
        )
        verify(rpcSubscription, times(2)).start()
        verify(rpcSubscription, times(3)).subscriptionName
        verify(coordinator, times(2)).followStatusChangesByName(eq(setOf(rpcSubscription.subscriptionName)))

        postStopEvent()
        verify(rpcSubscription, times(2)).close()
    }

    @Test
    fun `service starts when subscription handle status is UP`() {
        postConfigChangedEvent()

        postRegistrationStatusChangeEvent(LifecycleStatus.UP, subRegistrationHandle)

        verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())
    }
}
