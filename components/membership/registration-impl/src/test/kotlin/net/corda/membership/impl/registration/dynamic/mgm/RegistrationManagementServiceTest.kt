package net.corda.membership.impl.registration.dynamic.mgm

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.state.RegistrationState
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
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

    private val configServiceName = setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>())
    private val registrationHandle: RegistrationHandle = mock()
    private val configHandle: AutoCloseable = mock()
    private val subscription: StateAndEventSubscription<String, RegistrationState, RegistrationCommand> = mock()
    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(any()) } doReturn registrationHandle
    }

    private lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var configurationReadService: ConfigurationReadService
    private lateinit var subscriptionFactory: SubscriptionFactory
    private lateinit var memberInfoFactory: MemberInfoFactory
    private lateinit var membershipGroupReaderProvider: MembershipGroupReaderProvider
    private lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory
    private lateinit var membershipPersistenceClient: MembershipPersistenceClient
    private lateinit var membershipQueryClient: MembershipQueryClient

    private fun postStartEvent() = postEvent(StartEvent())
    private fun postStopEvent() = postEvent(StopEvent())

    private fun postRegistrationStatusChangeEvent(
        lifecycleStatus: LifecycleStatus = LifecycleStatus.UP
    ) = postEvent(RegistrationStatusChangeEvent(registrationHandle, lifecycleStatus))

    private fun postConfigChangedEvent() = postEvent(
        ConfigChangedEvent(
            setOf(MESSAGING_CONFIG),
            mapOf(MESSAGING_CONFIG to SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty()))
        )
    )

    private fun postEvent(event: LifecycleEvent) = lifecycleEventHandler.firstValue.processEvent(event, coordinator)

    val configs = setOf(BOOT_CONFIG, MESSAGING_CONFIG)

    @BeforeEach
    fun setUp() {
        lifecycleCoordinatorFactory = mock {
            on { createCoordinator(any(), lifecycleEventHandler.capture()) } doReturn coordinator
        }
        configurationReadService = mock {
            on { registerComponentForUpdates(eq(coordinator), eq(configs)) } doReturn configHandle
        }
        subscriptionFactory = mock {
            on { createStateAndEventSubscription(any(), any<RegistrationProcessor>(), any(), eq(null)) } doReturn subscription
        }
        memberInfoFactory = mock()
        membershipGroupReaderProvider = mock()
        cordaAvroSerializationFactory = mock()
        membershipPersistenceClient = mock()
        membershipQueryClient = mock()

        registrationManagementService = RegistrationManagementServiceImpl(
            lifecycleCoordinatorFactory,
            configurationReadService,
            subscriptionFactory,
            memberInfoFactory,
            membershipGroupReaderProvider,
            cordaAvroSerializationFactory,
            membershipPersistenceClient,
            membershipQueryClient
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
    fun `start event follow config read service`() {
        postStartEvent()
        verify(coordinator).followStatusChangesByName(eq(configServiceName))
        verify(registrationHandle, never()).close()

        postStartEvent()
        verify(coordinator, times(2)).followStatusChangesByName(eq(configServiceName))
        verify(registrationHandle).close()

        postStopEvent()
        verify(registrationHandle, times(2)).close()
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
    fun `config change event`() {
        postConfigChangedEvent()

        verify(subscription, never()).close()
        verify(subscriptionFactory)
            .createStateAndEventSubscription(any(), any<RegistrationProcessor>(), any(), eq(null))
        verify(subscription).start()
        verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())

        postConfigChangedEvent()
        verify(subscription).close()
        verify(subscriptionFactory, times(2))
            .createStateAndEventSubscription(any(), any<RegistrationProcessor>(), any(), eq(null))
        verify(subscription, times(2)).start()
        verify(coordinator, times(2)).updateStatus(eq(LifecycleStatus.UP), any())

        postStopEvent()
        verify(subscription, times(2)).close()
    }
}