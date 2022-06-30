package net.corda.membership.verification.service.impl

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.p2p.VerificationRequest
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.verification.service.MembershipVerificationService
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Membership.Companion.MEMBERSHIP_VERIFICATION_TOPIC
import net.corda.schema.configuration.ConfigKeys
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

class MembershipVerificationServiceTest {
    private val dependencyHandle: RegistrationHandle = mock()
    private val configHandle: AutoCloseable = mock()
    private val subHandle: RegistrationHandle = mock()

    private val subscriptionCoordinatorName = LifecycleCoordinatorName("SUB")

    private lateinit var membershipVerificationService: MembershipVerificationService

    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(any(), any()) } doReturn configHandle
    }

    private val subscription: Subscription<String, VerificationRequest> = mock {
        on { subscriptionName } doReturn subscriptionCoordinatorName
    }

    private val subscriptionFactory: SubscriptionFactory = mock {
        on {
            createDurableSubscription(any(), any<DurableProcessor<String, VerificationRequest>>(), any(), eq(null))
        } doReturn subscription
    }

    private val dependentComponents = setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
    )

    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(eq(dependentComponents)) } doReturn dependencyHandle
        on { followStatusChangesByName(eq(setOf(subscriptionCoordinatorName))) } doReturn subHandle
    }

    private val eventHandler: KArgumentCaptor<LifecycleEventHandler> = argumentCaptor()

    private val coordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), eventHandler.capture()) } doReturn coordinator
    }

    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock()

    private val testConfig =
        SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.parseString("instanceId=1"))

    @BeforeEach
    fun setUp() {
        membershipVerificationService = MembershipVerificationServiceImpl(
            coordinatorFactory,
            configurationReadService,
            subscriptionFactory,
            cordaAvroSerializationFactory
        )
    }

    @Test
    fun `Start event starts following the statuses of the required dependencies and closes dependency handle if needed`() {
        postStartEvent()

        verify(dependencyHandle, never()).close()
        verify(coordinator).followStatusChangesByName(
            eq(dependentComponents)
        )

        postStartEvent()

        verify(dependencyHandle).close()
        verify(coordinator, times(2)).followStatusChangesByName(eq(dependentComponents))

        postStopEvent()

        verify(dependencyHandle, times(2)).close()
    }

    @Test
    fun `Stop event closes the dependency handle`() {
        postStartEvent()

        verify(dependencyHandle, never()).close()

        postStopEvent()
        verify(dependencyHandle).close()
        verify(subscription, never()).close()
        verify(configHandle, never()).close()
    }

    @Test
    fun `ConfigChanged event creates subscription and it is closed after Stop event`() {
        postStartEvent()

        val configCaptor = argumentCaptor<SubscriptionConfig>()
        postConfigChangedEvent()
        verify(subscriptionFactory).createDurableSubscription(
            configCaptor.capture(),
            any<DurableProcessor<String, VerificationRequest>>(),
            any(),
            eq(null)
        )

        with(configCaptor.firstValue) {
            assertThat(this.eventTopic).isEqualTo(MEMBERSHIP_VERIFICATION_TOPIC)
        }

        postStopEvent()
        verify(subscription).close()
    }

    @Test
    fun `config handle is created and then closed after receiving another RegistrationStatusChange event`() {
        postStartEvent()
        postRegistrationStatusChangeEvent(LifecycleStatus.UP)
        verify(configurationReadService).registerComponentForUpdates(
            eq(coordinator),
            eq(setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG))
        )

        postRegistrationStatusChangeEvent(LifecycleStatus.UP)
        verify(configHandle).close()
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
    fun `service starts when subscription handle status is UP`() {
        postConfigChangedEvent()

        postRegistrationStatusChangeEvent(LifecycleStatus.UP, subHandle)

        verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())
    }

    @Test
    fun `status is set to DOWN after receiving Stop event`() {
        postStopEvent()

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        verify(configHandle, never()).close()
        verify(dependencyHandle, never()).close()
        verify(subscription, never()).close()
    }

    private fun postStartEvent() {
        eventHandler.firstValue.processEvent(StartEvent(), coordinator)
    }

    private fun postStopEvent() {
        eventHandler.firstValue.processEvent(StopEvent(), coordinator)
    }

    private fun postConfigChangedEvent() {
        eventHandler.firstValue.processEvent(
            ConfigChangedEvent(
                setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG),
                mapOf(
                    ConfigKeys.BOOT_CONFIG to testConfig,
                    ConfigKeys.MESSAGING_CONFIG to testConfig
                )
            ), coordinator
        )
    }

    private fun postRegistrationStatusChangeEvent(
        status: LifecycleStatus,
        handle: RegistrationHandle = dependencyHandle
    ) {
        eventHandler.firstValue.processEvent(
            RegistrationStatusChangeEvent(
                handle,
                status
            ), coordinator
        )
    }
}