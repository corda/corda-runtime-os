package net.corda.components.scheduler

import net.corda.components.scheduler.impl.TriggerPublisherImpl
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.ConfigKeys
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.time.Instant
import java.time.temporal.ChronoUnit

class TriggerPublisherTest {
    private val smartConfig = mock<SmartConfig>()
    private val config = mapOf(
        ConfigKeys.MESSAGING_CONFIG to smartConfig
    )
    private val lifecycleCoordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) } doReturn lifecycleCoordinator
    }
    private val configSubscription = mock<Resource>()
    private val configurationReadService = mock<ConfigurationReadService> {
        on { registerComponentForUpdates(any(), any()) } doReturn configSubscription
    }
    private val kafkaPublisher = mock<Publisher>()
    private val publisherFactory = mock<PublisherFactory> {
        on { createPublisher(any(), any()) } doReturn kafkaPublisher
    }

    @Test
    fun `when publish publish trigger to messagebus`() {
        val now = Instant.now()
        val publisher = TriggerPublisherImpl(
            lifecycleCoordinatorFactory, configurationReadService, publisherFactory
        ) { now }

        // make sure publisher is set
        publisher.processEvent(
            ConfigChangedEvent(setOf(ConfigKeys.MESSAGING_CONFIG), config), lifecycleCoordinator)

        publisher.publish("foo", "bar")

        verify(kafkaPublisher).publish(argThat {
                records: List<Record<*, *>> ->
            val record = records.single()
            val recordValue = record.value as ScheduledTaskTrigger
            record.key =="foo"
                    && record.topic == "bar"
                    && recordValue.name == "foo"
                    && recordValue.timestamp.truncatedTo(ChronoUnit.MILLIS) == now.truncatedTo(ChronoUnit.MILLIS)
        })
    }

    @Test
    fun `when ConfigChangedEvent create publisher and status UP`() {
        val now = Instant.now()
        val publisher = TriggerPublisherImpl(
            lifecycleCoordinatorFactory, configurationReadService, publisherFactory
        ) { now }

        publisher.processEvent(
            ConfigChangedEvent(setOf(ConfigKeys.MESSAGING_CONFIG), config), lifecycleCoordinator)

        verify(publisherFactory).createPublisher(any(), any())
        verify(lifecycleCoordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `when ConfigChangedEvent without config don't set status UP`() {
        val now = Instant.now()
        val publisher = TriggerPublisherImpl(
            lifecycleCoordinatorFactory, configurationReadService, publisherFactory
        ) { now }

        publisher.processEvent(
            ConfigChangedEvent(setOf(ConfigKeys.MESSAGING_CONFIG), mapOf(
                "foo" to smartConfig
            )), lifecycleCoordinator)

        verify(publisherFactory, times(0)).createPublisher(any(), any())
        verify(lifecycleCoordinator, times(0)).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `when ConfigChangedEvent second time, close publisher before creating new`() {
        val now = Instant.now()
        val publisher = TriggerPublisherImpl(
            lifecycleCoordinatorFactory, configurationReadService, publisherFactory
        ) { now }

        publisher.processEvent(
            ConfigChangedEvent(setOf(ConfigKeys.MESSAGING_CONFIG), config), lifecycleCoordinator)

        verify(publisherFactory).createPublisher(any(), any())

        publisher.processEvent(
            ConfigChangedEvent(setOf(ConfigKeys.MESSAGING_CONFIG), config), lifecycleCoordinator)

        verify(kafkaPublisher, times(1)).close()
    }

    @Test
    fun `when StartEvent start configReadService`() {
        val now = Instant.now()
        val publisher = TriggerPublisherImpl(
            lifecycleCoordinatorFactory, configurationReadService, publisherFactory
        ) { now }

        publisher.processEvent(
            StartEvent(), lifecycleCoordinator)

        verify(configurationReadService).start()
        verify(lifecycleCoordinator).followStatusChangesByName(argThat {
                names: Set<LifecycleCoordinatorName> ->
            names.contains(LifecycleCoordinatorName(ConfigurationReadService::class.java.name))
        })
    }

    @Test
    fun `when RegistrationStatusChangeEvent UP register configReadService`() {
        val now = Instant.now()
        val publisher = TriggerPublisherImpl(
            lifecycleCoordinatorFactory, configurationReadService, publisherFactory
        ) { now }

        publisher.processEvent(
            RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), lifecycleCoordinator)

        verify(configurationReadService).registerComponentForUpdates(
            eq(lifecycleCoordinator),
            argThat {
                keys: Set<String> ->
            keys.contains(ConfigKeys.MESSAGING_CONFIG)
        })
    }

    @Test
    fun `when RegistrationStatusChangeEvent DOWN update status`() {
        val now = Instant.now()
        val publisher = TriggerPublisherImpl(
            lifecycleCoordinatorFactory, configurationReadService, publisherFactory
        ) { now }

        publisher.processEvent(
            RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), lifecycleCoordinator)

        verify(lifecycleCoordinator).updateStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `when RegistrationStatusChangeEvent DOWN after UP close dependencies`() {
        val now = Instant.now()
        val publisher = TriggerPublisherImpl(
            lifecycleCoordinatorFactory, configurationReadService, publisherFactory
        ) { now }

        publisher.processEvent(
            ConfigChangedEvent(setOf(ConfigKeys.MESSAGING_CONFIG), config), lifecycleCoordinator)
        publisher.processEvent(
            RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), lifecycleCoordinator)

        publisher.processEvent(
            RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), lifecycleCoordinator)

        verify(configSubscription).close()
        verify(kafkaPublisher).close()
    }
}