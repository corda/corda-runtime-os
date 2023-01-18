package net.corda.membership.impl.p2p

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.hes.StableKeyPairDecryptor
import net.corda.libs.configuration.SmartConfigFactoryFactory
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
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.data.p2p.app.AppMessage
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.registry.AvroSchemaRegistry
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

class MembershipP2PReadServiceImplTest {

    lateinit var membershipP2PReadServiceImpl: MembershipP2PReadServiceImpl

    private var eventHandlerCaptor = argumentCaptor<LifecycleEventHandler>()
    private val registrationHandle: RegistrationHandle = mock()
    private val subRegistrationHandle: RegistrationHandle = mock()
    private val configHandle: Resource = mock()
    private val subscription: Subscription<String, AppMessage> = mock()
    private val dependencies = setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
        LifecycleCoordinatorName.forComponent<StableKeyPairDecryptor>(),
        LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
    )

    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(any()) } doReturn subRegistrationHandle
        on { followStatusChangesByName(eq(dependencies)) } doReturn registrationHandle
    }

    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), eventHandlerCaptor.capture()) } doReturn coordinator
    }
    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(eq(coordinator), any()) } doReturn configHandle
    }
    private val subscriptionFactory: SubscriptionFactory = mock {
        on {
            createDurableSubscription(
                any(),
                any<DurableProcessor<String, AppMessage>>(),
                any(),
                eq(null)
            )
        } doReturn subscription
    }
    private val avroSchemaRegistry: AvroSchemaRegistry = mock()
    private val stableKeyPairDecryptor: StableKeyPairDecryptor = mock()
    private val keyEncodingService: KeyEncodingService = mock()
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider = mock()

    private val testConfig =
        SmartConfigFactoryFactory.createWithoutSecurityServices().create(ConfigFactory.parseString("instanceId=1"))

    @BeforeEach
    fun setUp() {
        membershipP2PReadServiceImpl = MembershipP2PReadServiceImpl(
            lifecycleCoordinatorFactory,
            configurationReadService,
            subscriptionFactory,
            avroSchemaRegistry,
            stableKeyPairDecryptor,
            keyEncodingService,
            membershipGroupReaderProvider,
        )
    }

    @Test
    fun `start starts coordinator`() {
        membershipP2PReadServiceImpl.start()

        verify(coordinator).start()
    }

    @Test
    fun `stop stops coordinator`() {
        membershipP2PReadServiceImpl.stop()

        verify(coordinator).stop()
    }

    fun postStartEvent() {
        eventHandlerCaptor.firstValue.processEvent(StartEvent(), coordinator)
    }

    fun postStopEvent() {
        eventHandlerCaptor.firstValue.processEvent(StopEvent(), coordinator)
    }

    @Test
    fun `start event creates new registration handle`() {
        postStartEvent()

        verify(registrationHandle, never()).close()
        verify(coordinator).followStatusChangesByName(eq(dependencies))
    }

    @Test
    fun `start event closes old registration handle and creates new registration handle if one exists`() {
        postStartEvent()
        postStartEvent()

        verify(registrationHandle).close()
        verify(coordinator, times(2)).followStatusChangesByName(eq(dependencies))
    }

    @Test
    fun `stop event sets status to down but closes no handles or subscriptions if they don't exist yet`() {
        postStopEvent()

        verify(registrationHandle, never()).close()
        verify(configHandle, never()).close()
        verify(subscription, never()).close()
        verify(coordinator).updateStatus(
            eq(LifecycleStatus.DOWN), any()
        )
    }

    @Test
    fun `stop event sets status to down and closes handles and subscription when they have been created`() {
        postStartEvent()
        eventHandlerCaptor.firstValue.processEvent(
            RegistrationStatusChangeEvent(
                registrationHandle, LifecycleStatus.UP
            ),
            coordinator
        )
        eventHandlerCaptor.firstValue.processEvent(
            ConfigChangedEvent(
                setOf(BOOT_CONFIG, MESSAGING_CONFIG),
                mapOf(
                    BOOT_CONFIG to testConfig,
                    MESSAGING_CONFIG to testConfig
                )
            ), coordinator
        )
        postStopEvent()

        verify(registrationHandle).close()
        verify(configHandle).close()
        verify(subscription, times(2)).close()
        verify(coordinator).updateStatus(
            eq(LifecycleStatus.DOWN), any()
        )
    }

    @Test
    fun `registration status change to UP follows config changes`() {
        postStartEvent()
        eventHandlerCaptor.firstValue.processEvent(
            RegistrationStatusChangeEvent(
                registrationHandle, LifecycleStatus.UP
            ),
            coordinator
        )

        verify(configHandle, never()).close()
        verify(configurationReadService).registerComponentForUpdates(
            eq(coordinator),
            eq(setOf(BOOT_CONFIG, MESSAGING_CONFIG))
        )
    }

    @Test
    fun `registration status change to UP a second time recreates the config change handle`() {
        postStartEvent()
        eventHandlerCaptor.firstValue.processEvent(
            RegistrationStatusChangeEvent(
                registrationHandle, LifecycleStatus.UP
            ),
            coordinator
        )
        eventHandlerCaptor.firstValue.processEvent(
            RegistrationStatusChangeEvent(
                registrationHandle, LifecycleStatus.UP
            ),
            coordinator
        )

        verify(configHandle).close()
        verify(configurationReadService, times(2)).registerComponentForUpdates(
            eq(coordinator),
            eq(setOf(BOOT_CONFIG, MESSAGING_CONFIG))
        )
    }

    @Test
    fun `registration status change to DOWN set the component status to down`() {
        postStartEvent()
        eventHandlerCaptor.firstValue.processEvent(
            RegistrationStatusChangeEvent(
                registrationHandle, LifecycleStatus.DOWN
            ),
            coordinator
        )

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        verify(subscription, never()).close()
    }

    @Test
    fun `registration status change to DOWN set the component status to down and closes the subscription if already created`() {
        postStartEvent()
        eventHandlerCaptor.firstValue.processEvent(
            ConfigChangedEvent(
                setOf(BOOT_CONFIG, MESSAGING_CONFIG),
                mapOf(
                    BOOT_CONFIG to testConfig,
                    MESSAGING_CONFIG to testConfig
                )
            ), coordinator
        )
        eventHandlerCaptor.firstValue.processEvent(
            RegistrationStatusChangeEvent(
                registrationHandle, LifecycleStatus.DOWN
            ),
            coordinator
        )

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        verify(subscription, times(2)).close()
    }

    @Test
    fun `Config changed event creates subscription`() {
        eventHandlerCaptor.firstValue.processEvent(
            ConfigChangedEvent(
                setOf(BOOT_CONFIG, MESSAGING_CONFIG),
                mapOf(
                    BOOT_CONFIG to testConfig,
                    MESSAGING_CONFIG to testConfig
                )
            ), coordinator
        )

        verify(subscription, never()).close()
        verify(subscriptionFactory, times(2)).createDurableSubscription(
            any(),
            any<DurableProcessor<String, AppMessage>>(),
            any(),
            eq(null)
        )
        verify(subscription, times(2)).start()
        verify(coordinator).followStatusChangesByName(any())
    }

    @Test
    fun `Component starts after subscription is UP`() {
        eventHandlerCaptor.firstValue.processEvent(
            ConfigChangedEvent(
                setOf(BOOT_CONFIG, MESSAGING_CONFIG),
                mapOf(
                    BOOT_CONFIG to testConfig,
                    MESSAGING_CONFIG to testConfig
                )
            ), coordinator
        )

        eventHandlerCaptor.firstValue.processEvent(
            RegistrationStatusChangeEvent(
                subRegistrationHandle,
                LifecycleStatus.UP
            ), coordinator
        )

        verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())
    }

    @Test
    fun `Config changed event closes original subscription before creating a new one`() {
        eventHandlerCaptor.firstValue.processEvent(
            ConfigChangedEvent(
                setOf(BOOT_CONFIG, MESSAGING_CONFIG),
                mapOf(
                    BOOT_CONFIG to testConfig,
                    MESSAGING_CONFIG to testConfig
                )
            ), coordinator
        )
        eventHandlerCaptor.firstValue.processEvent(
            ConfigChangedEvent(
                setOf(BOOT_CONFIG, MESSAGING_CONFIG),
                mapOf(
                    BOOT_CONFIG to testConfig,
                    MESSAGING_CONFIG to testConfig
                )
            ), coordinator
        )

        verify(subscription, times(2)).close()
        verify(subscriptionFactory, times(4)).createDurableSubscription(
            any(),
            any<DurableProcessor<String, AppMessage>>(),
            any(),
            eq(null)
        )
        verify(subscription, times(4)).start()
        verify(coordinator, times(2)).followStatusChangesByName(any())
    }
}
