package net.corda.membership.impl.registration.dynamic

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.state.RegistrationState
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.RegistrationManagementService
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MEMBERSHIP_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Captor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class RegistrationManagementServiceTest {

    private lateinit var registrationManagementService: RegistrationManagementService

    @Captor
    val lifecycleEventHandler = argumentCaptor<LifecycleEventHandler>()

    private val configs = setOf(BOOT_CONFIG, MESSAGING_CONFIG, MEMBERSHIP_CONFIG)
    private val dependencyServices = setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
        LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
        LifecycleCoordinatorName.forComponent<MembershipPersistenceClient>(),
        LifecycleCoordinatorName.forComponent<MembershipQueryClient>(),
    )
    private val registrationHandle: RegistrationHandle = mock()
    private val configHandle: Resource = mock()
    private val subscription: StateAndEventSubscription<String, RegistrationState, RegistrationCommand> = mock()
    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(any()) } doReturn registrationHandle
    }

    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), lifecycleEventHandler.capture()) } doReturn coordinator
    }
    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(eq(coordinator), eq(configs)) } doReturn configHandle
    }
    private val subscriptionFactory: SubscriptionFactory = mock {
        on { createStateAndEventSubscription(any(), any<RegistrationProcessor>(), any(), eq(null)) } doReturn subscription
    }
    private val memberInfoFactory: MemberInfoFactory = mock()
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider = mock()
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroDeserializer(any(), any<Class<*>>()) } doReturn mock()
    }
    private val membershipPersistenceClient: MembershipPersistenceClient = mock()
    private val membershipQueryClient: MembershipQueryClient = mock()

    private fun postStartEvent() = postEvent(StartEvent())
    private fun postStopEvent() = postEvent(StopEvent())

    private fun postRegistrationStatusChangeEvent(
        lifecycleStatus: LifecycleStatus = LifecycleStatus.UP
    ) = postEvent(RegistrationStatusChangeEvent(registrationHandle, lifecycleStatus))

    private fun postConfigChangedEvent() = postEvent(
        ConfigChangedEvent(
            setOf(MESSAGING_CONFIG, MEMBERSHIP_CONFIG),
            mapOf(
                MESSAGING_CONFIG to mock(),
                MEMBERSHIP_CONFIG to mock(),
            )
        )
    )

    private fun postEvent(event: LifecycleEvent) = lifecycleEventHandler.firstValue.processEvent(event, coordinator)

    @BeforeEach
    fun setUp() {
        registrationManagementService = RegistrationManagementServiceImpl(
            lifecycleCoordinatorFactory,
            configurationReadService,
            subscriptionFactory,
            memberInfoFactory,
            membershipGroupReaderProvider,
            cordaAvroSerializationFactory,
            membershipPersistenceClient,
            membershipQueryClient,
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
        )
    }

    @Test
    fun `start starts the coordinator`() {
        registrationManagementService.start()
        verify(coordinator).start()
    }

    @Test
    fun `stop stops the coordinator`() {
        registrationManagementService.stop()
        verify(coordinator).stop()
    }

    @Test
    fun `start event follows config read service`() {
        postStartEvent()
        verify(coordinator).followStatusChangesByName(eq(dependencyServices))
        verify(registrationHandle, never()).close()
    }

    @Test
    fun `start event follows config read service and closes registration handle if it already exists`() {
        postStartEvent()
        postStartEvent()
        verify(coordinator, times(2)).followStatusChangesByName(eq(dependencyServices))
        verify(registrationHandle).close()
    }

    @Test
    fun `stop event closes registration handle`() {
        postStartEvent()
        postStopEvent()
        verify(registrationHandle).close()
    }

    @Test
    fun `stop event`() {
        postStopEvent()

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        verify(registrationHandle, never()).close()
        verify(configHandle, never()).close()
        verify(subscription, never()).close()
    }

    @Test
    fun `registration status lifecycle status down event`() {
        postRegistrationStatusChangeEvent(LifecycleStatus.DOWN)

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        verify(subscription, never()).close()
    }

    @Test
    fun `registration status lifecycle status error event`() {
        postRegistrationStatusChangeEvent(LifecycleStatus.ERROR)

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        verify(subscription, never()).close()
    }

    @Test
    fun `registration status lifecycle status up event`() {
        postStartEvent()
        postRegistrationStatusChangeEvent()

        verify(coordinator, never()).updateStatus(eq(LifecycleStatus.UP), any())
        verify(configHandle, never()).close()
        verify(configurationReadService).registerComponentForUpdates(eq(coordinator), eq(configs))

        postRegistrationStatusChangeEvent()
        verify(coordinator, never()).updateStatus(eq(LifecycleStatus.UP), any())
        verify(configHandle).close()
        verify(configurationReadService, times(2)).registerComponentForUpdates(eq(coordinator), eq(configs))

        postStopEvent()
        verify(configHandle, times(2)).close()
    }

    @Test
    fun `registration status lifecycle status up event additional times closes existing config handle`() {
        postStartEvent()
        postRegistrationStatusChangeEvent()
        postRegistrationStatusChangeEvent()
        verify(coordinator, never()).updateStatus(eq(LifecycleStatus.UP), any())
        verify(configHandle).close()
        verify(configurationReadService, times(2)).registerComponentForUpdates(eq(coordinator), eq(configs))
    }

    @Test
    fun `stop event closes config handle`() {
        postStartEvent()
        postRegistrationStatusChangeEvent()
        postStopEvent()
        verify(configHandle).close()
    }

    @Test
    fun `config change event`() {
        postConfigChangedEvent()

        verify(subscription, never()).close()
        verify(subscriptionFactory)
            .createStateAndEventSubscription(any(), any<RegistrationProcessor>(), any(), eq(null))
        verify(subscription).start()
        verify(coordinator, never()).updateStatus(eq(LifecycleStatus.UP), any())
        verify(coordinator).followStatusChangesByName(eq(setOf(subscription.subscriptionName)))

        postConfigChangedEvent()
        verify(subscription).close()
        verify(subscriptionFactory, times(2))
            .createStateAndEventSubscription(any(), any<RegistrationProcessor>(), any(), eq(null))
        verify(subscription, times(2)).start()
        verify(coordinator, times(2)).followStatusChangesByName(eq(setOf(subscription.subscriptionName)))

        postStopEvent()
        verify(subscription, times(2)).close()
    }
}
