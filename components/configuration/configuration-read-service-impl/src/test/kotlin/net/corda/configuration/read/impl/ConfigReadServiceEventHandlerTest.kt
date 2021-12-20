package net.corda.configuration.read.impl

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadException
import net.corda.libs.configuration.SmartConfigFactoryImpl
import net.corda.libs.configuration.read.ConfigListener
import net.corda.libs.configuration.read.ConfigReader
import net.corda.libs.configuration.read.factory.ConfigReaderFactory
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.capture
import org.mockito.kotlin.firstValue
import org.mockito.kotlin.mock
import org.mockito.kotlin.secondValue
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

internal class ConfigReadServiceEventHandlerTest {

    @Captor
    val lifecycleEventCaptor: ArgumentCaptor<LifecycleEvent> = ArgumentCaptor.forClass(LifecycleEvent::class.java)

    @Captor
    val lifecycleStatusCaptor: ArgumentCaptor<LifecycleStatus> = ArgumentCaptor.forClass(LifecycleStatus::class.java)

    // Mocks
    private lateinit var configReaderFactory: ConfigReaderFactory
    private lateinit var callbackHandles: ConfigurationHandlerStorage
    private lateinit var coordinator: LifecycleCoordinator
    private lateinit var configReader: ConfigReader

    private lateinit var configReadServiceEventHandler: ConfigReadServiceEventHandler

    @Captor
    private val callbackCaptor = argumentCaptor<ConfigListener>()

    @BeforeEach
    fun setUp() {
        configReaderFactory = mock()
        callbackHandles = mock()
        coordinator = mock()
        configReader = mock()
        `when`(configReaderFactory.createReader(any())).thenReturn(configReader)

        configReadServiceEventHandler = ConfigReadServiceEventHandler(configReaderFactory, callbackHandles)
    }

    @Test
    fun `BootstrapConfigProvided has correct output`() {
        configReadServiceEventHandler.processEvent(BootstrapConfigProvided(mock()), coordinator)
        verify(coordinator).postEvent(capture(lifecycleEventCaptor))
        assertThat(lifecycleEventCaptor.firstValue is SetupSubscription)
    }

    @Test
    fun `Event handler works when states in correct order`() {
        configReadServiceEventHandler.processEvent(BootstrapConfigProvided(mock()), coordinator)
        configReadServiceEventHandler.processEvent(SetupSubscription(), coordinator)
        `when`(coordinator.status).thenReturn(LifecycleStatus.DOWN)

        verify(configReader).start()
        verify(callbackHandles).addSubscription(any())
        verify(configReader, times(1)).registerCallback(callbackCaptor.capture())

        // This callback should trigger the UP state as it's a "snapshot"
        callbackCaptor.firstValue.onUpdate(emptySet(), emptyMap())

        verify(coordinator).updateStatus(capture(lifecycleStatusCaptor), any())
        assertThat(lifecycleStatusCaptor.firstValue).isEqualTo(LifecycleStatus.UP)
    }

    @Test
    fun `Start event works when bootstrap config provided`() {
        configReadServiceEventHandler.processEvent(BootstrapConfigProvided(mock()), coordinator)
        configReadServiceEventHandler.processEvent(StartEvent(), coordinator)
        // The first value captured will be from the BootstrapConfig being provided
        verify(coordinator, times(2)).postEvent(capture(lifecycleEventCaptor))
        assertThat(lifecycleEventCaptor.secondValue is SetupSubscription)
    }

    @Test
    fun `Start event fails when bootstrap config not provided`() {
        configReadServiceEventHandler.processEvent(StartEvent(), coordinator)
        // The first value captured will be from the BootstrapConfig being provided
        verifyNoInteractions(coordinator)
    }

    @Test
    fun `Stop event removes the subscription`() {
        configReadServiceEventHandler.processEvent(BootstrapConfigProvided(mock()), coordinator)
        configReadServiceEventHandler.processEvent(SetupSubscription(), coordinator)
        configReadServiceEventHandler.processEvent(StopEvent(), coordinator)
        // The first value captured will be from the BootstrapConfig being provided
        verify(callbackHandles).removeSubscription()
        verify(configReader).stop()
    }

    @Test
    fun `Error event means nothing happens`() {
        configReadServiceEventHandler.processEvent(ErrorEvent(Exception()), coordinator)
        // The first value captured will be from the BootstrapConfig being provided
        verifyNoInteractions(coordinator)
        verifyNoInteractions(configReaderFactory)
        verifyNoInteractions(configReader)
        verifyNoInteractions(callbackHandles)
    }

    @Test
    fun `Multiple bootstrap events with same config are ignored`() {
        val configA = SmartConfigFactoryImpl().create(ConfigFactory.parseMap(mapOf("foo" to "bar", "bar" to "baz")))
        val configB = SmartConfigFactoryImpl().create(ConfigFactory.parseMap(mapOf("bar" to "baz", "foo" to "bar")))
        configReadServiceEventHandler.processEvent(BootstrapConfigProvided(configA), coordinator)
        configReadServiceEventHandler.processEvent(BootstrapConfigProvided(configB), coordinator)
        verify(coordinator, times(1)).postEvent(capture(lifecycleEventCaptor))
        assertThat(lifecycleEventCaptor.firstValue is SetupSubscription)
    }

    @Test
    fun `Multiple bootstrap events with different config raises an error`() {
        val configA = SmartConfigFactoryImpl().create(ConfigFactory.parseMap(mapOf("foo" to "bar", "bar" to "baz")))
        val configB = SmartConfigFactoryImpl().create(ConfigFactory.parseMap(mapOf("bar" to "baz", "foo" to "foo")))
        configReadServiceEventHandler.processEvent(BootstrapConfigProvided(configA), coordinator)
        assertThrows<ConfigurationReadException> {
            configReadServiceEventHandler.processEvent(BootstrapConfigProvided(configB), coordinator)
        }
    }
}
