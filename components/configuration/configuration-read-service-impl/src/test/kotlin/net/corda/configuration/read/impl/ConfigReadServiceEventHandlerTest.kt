package net.corda.configuration.read.impl

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadException
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.firstValue
import org.mockito.kotlin.mock
import org.mockito.kotlin.secondValue
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

internal class ConfigReadServiceEventHandlerTest {
    private val configFactory = SmartConfigFactory.createWithoutSecurityServices()

    @Captor
    val lifecycleEventCaptor: ArgumentCaptor<LifecycleEvent> = ArgumentCaptor.forClass(LifecycleEvent::class.java)

    @Captor
    val lifecycleStatusCaptor: ArgumentCaptor<LifecycleStatus> = ArgumentCaptor.forClass(LifecycleStatus::class.java)

    // Mocks
    private lateinit var subscriptionFactory: SubscriptionFactory
    private lateinit var coordinator: LifecycleCoordinator
    private lateinit var compactedSubscription: CompactedSubscription<String, SmartConfig>
    private lateinit var configMerger: ConfigMerger

    private lateinit var configReadServiceEventHandler: ConfigReadServiceEventHandler

    private val smartConfigFactory = SmartConfigFactory.createWithoutSecurityServices()
    private val bootConfig = smartConfigFactory.create(ConfigFactory.parseMap(mapOf("start" to 1)))
    @BeforeEach
    fun setUp() {
        subscriptionFactory = mock()
        coordinator = mock()
        compactedSubscription = mock()
        `when`(subscriptionFactory.createCompactedSubscription<String, SmartConfig>(any(), any(), any())).thenReturn(
            compactedSubscription
        )
        configMerger  = mock {
            on { getMessagingConfig(bootConfig, null) } doAnswer { SmartConfigImpl.empty() }
            on { getDbConfig(any(), any()) } doAnswer { it.arguments[1] as SmartConfig  }
        }

        configReadServiceEventHandler = ConfigReadServiceEventHandler(subscriptionFactory, configMerger)
    }

    @Test
    fun `BootstrapConfigProvided triggers SetupSubscription to be sent on new config`() {
        configReadServiceEventHandler.processEvent(BootstrapConfigProvided(bootConfig), coordinator)
        verify(coordinator).postEvent(capture(lifecycleEventCaptor))
        assertThat(lifecycleEventCaptor.firstValue is SetupConfigSubscription)
    }

    @Test
    fun `Event handler works when states in correct order`() {
        configReadServiceEventHandler.processEvent(BootstrapConfigProvided(bootConfig), coordinator)
        configReadServiceEventHandler.processEvent(SetupConfigSubscription(), coordinator)
        `when`(coordinator.status).thenReturn(LifecycleStatus.DOWN)

        verify(compactedSubscription).start()

        configReadServiceEventHandler.processEvent(
            RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP),
            coordinator
        )
        verify(coordinator).updateStatus(capture(lifecycleStatusCaptor), any())
        assertThat(lifecycleStatusCaptor.firstValue).isEqualTo(LifecycleStatus.UP)
    }

    @Test
    fun `Start event works when bootstrap config provided`() {
        configReadServiceEventHandler.processEvent(BootstrapConfigProvided(bootConfig), coordinator)
        configReadServiceEventHandler.processEvent(StartEvent(), coordinator)
        // The first value captured will be from the BootstrapConfig being provided
        verify(coordinator, times(2)).postEvent(capture(lifecycleEventCaptor))
        assertThat(lifecycleEventCaptor.secondValue is SetupConfigSubscription)
    }

    @Test
    fun `Start event does not trigger subscription when bootstrap config not provided`() {
        configReadServiceEventHandler.processEvent(StartEvent(), coordinator)
        // The first value captured will be from the BootstrapConfig being provided
        verifyNoInteractions(coordinator)
    }

    @Test
    fun `Stop event removes the subscription`() {
        configReadServiceEventHandler.processEvent(BootstrapConfigProvided(bootConfig), coordinator)
        configReadServiceEventHandler.processEvent(SetupConfigSubscription(), coordinator)
        configReadServiceEventHandler.processEvent(StopEvent(), coordinator)
        verify(compactedSubscription).close()
    }

    @Test
    fun `Error event means nothing happens`() {
        configReadServiceEventHandler.processEvent(ErrorEvent(Exception()), coordinator)
        // The first value captured will be from the BootstrapConfig being provided
        verifyNoInteractions(coordinator)
        verifyNoInteractions(subscriptionFactory)
        verifyNoInteractions(compactedSubscription)
    }

    @Test
    fun `adding a registration updates it with current configuration`() {
        setupHandlerForProcessingConfig()
        val newConfig = smartConfigFactory.create(ConfigFactory.parseMap(mapOf("foo" to "bar")))
        val registration = ConfigurationChangeRegistration(coordinator) { keys, config ->
            assertEquals(setOf(BOOT_CONFIG, FLOW_CONFIG), keys)
            assertEquals(mapOf(BOOT_CONFIG to bootConfig, FLOW_CONFIG to newConfig), config)
        }
        configReadServiceEventHandler.processEvent(NewConfigReceived(mapOf(FLOW_CONFIG to newConfig)), coordinator)
        configReadServiceEventHandler.processEvent(ConfigRegistrationAdd(registration), coordinator)
    }

    @Test
    fun `all current registrations are updated when a config update happens`() {
        setupHandlerForProcessingConfig()
        val newConfig = smartConfigFactory.create(ConfigFactory.parseMap(mapOf("foo" to "bar")))
        val reg1 = ConfigurationChangeRegistration(coordinator) { keys, config ->
            if (keys.contains(FLOW_CONFIG)) {
                assertEquals(setOf(FLOW_CONFIG), keys)
                assertEquals(mapOf(BOOT_CONFIG to bootConfig, FLOW_CONFIG to newConfig), config)
            }
        }
        val reg2 = ConfigurationChangeRegistration(coordinator) { keys, config ->
            if (keys.contains(FLOW_CONFIG)) {
                assertEquals(setOf(FLOW_CONFIG), keys)
                assertEquals(mapOf(BOOT_CONFIG to bootConfig, FLOW_CONFIG to newConfig), config)
            }
        }
        configReadServiceEventHandler.processEvent(ConfigRegistrationAdd(reg1), coordinator)
        configReadServiceEventHandler.processEvent(ConfigRegistrationAdd(reg2), coordinator)
        configReadServiceEventHandler.processEvent(NewConfigReceived(mapOf(FLOW_CONFIG to newConfig)), coordinator)
    }

    @Test
    fun `removing a registration stops updates being delivered`() {
        setupHandlerForProcessingConfig()
        val newConfig = smartConfigFactory.create(ConfigFactory.parseMap(mapOf("foo" to "bar")))
        var shouldCallReg = true
        val reg = ConfigurationChangeRegistration(coordinator) { _, _ ->
            assertTrue(shouldCallReg)
        }
        configReadServiceEventHandler.processEvent(ConfigRegistrationAdd(reg), coordinator)
        shouldCallReg = false
        configReadServiceEventHandler.processEvent(ConfigRegistrationRemove(reg), coordinator)
        configReadServiceEventHandler.processEvent(NewConfigReceived(mapOf(FLOW_CONFIG to newConfig)), coordinator)
    }

    @Test
    fun `Multiple bootstrap events with same config are ignored`() {
        val configA = configFactory.create(ConfigFactory.parseMap(mapOf("foo" to "bar", "bar" to "baz")))
        val configB = configFactory.create(ConfigFactory.parseMap(mapOf("bar" to "baz", "foo" to "bar")))
        configReadServiceEventHandler.processEvent(BootstrapConfigProvided(configA), coordinator)
        configReadServiceEventHandler.processEvent(BootstrapConfigProvided(configB), coordinator)
        verify(coordinator, times(1)).postEvent(capture(lifecycleEventCaptor))
        assertThat(lifecycleEventCaptor.firstValue is SetupConfigSubscription)
    }

    @Test
    fun `Multiple bootstrap events with different config raises an error`() {
        val configA = configFactory.create(ConfigFactory.parseMap(mapOf("foo" to "bar", "bar" to "baz")))
        val configB = configFactory.create(ConfigFactory.parseMap(mapOf("bar" to "baz", "foo" to "foo")))
        configReadServiceEventHandler.processEvent(BootstrapConfigProvided(configA), coordinator)
        assertThrows<ConfigurationReadException> {
            configReadServiceEventHandler.processEvent(BootstrapConfigProvided(configB), coordinator)
        }
    }

    private fun setupHandlerForProcessingConfig() {
        configReadServiceEventHandler.processEvent(BootstrapConfigProvided(bootConfig), coordinator)
    }
}
