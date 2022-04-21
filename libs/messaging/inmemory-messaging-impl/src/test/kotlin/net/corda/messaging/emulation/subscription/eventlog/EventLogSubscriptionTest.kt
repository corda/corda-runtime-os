package net.corda.messaging.emulation.subscription.eventlog

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.emulation.topic.model.Consumption
import net.corda.messaging.emulation.topic.service.TopicService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EventLogSubscriptionTest {
    private val consumption = mock<Consumption>()
    private val topic = mock<TopicService> {
        on { createConsumption(any()) } doReturn consumption
    }
    private val lifecycleCoordinator: LifecycleCoordinator = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn lifecycleCoordinator
    }
    private val config = SubscriptionConfig(eventTopic = "topic", groupName = "group")
    private val subscription = EventLogSubscription<String, SubscriptionConfig>(
        subscriptionConfig = config,
        processor = mock(),
        partitionAssignmentListener = null,
        topicService = topic,
        lifecycleCoordinatorFactory,
        1
    )

    @Test
    fun `start will subscribe a consumer`() {
        subscription.start()

        verify(topic).createConsumption(any())
    }

    @Test
    fun `double start will subscribe a consumer only once`() {
        subscription.start()
        subscription.start()

        verify(topic, times(1)).createConsumption(any())
    }

    @Test
    fun `stop will stop the lifecycle`() {
        subscription.start()
        subscription.stop()

        verify(consumption).stop()
    }

    @Test
    fun `second stop will stop the lifecycle only once`() {
        subscription.start()
        subscription.stop()
        subscription.stop()

        verify(consumption, times(1)).stop()
    }

    @Test
    fun `isRunning will return false if had not started`() {

        assertThat(subscription.isRunning).isFalse
    }

    @Test
    fun `isRunning will return false if had thread was killed`() {
        subscription.start()
        whenever(consumption.isRunning).doReturn(false)

        assertThat(subscription.isRunning).isFalse
    }

    @Test
    fun `isRunning will return true if had thread is alive`() {
        subscription.start()
        whenever(consumption.isRunning).doReturn(true)

        assertThat(subscription.isRunning).isTrue
    }

    @Test
    fun `topicName return the correct value`() {
        assertThat(subscription.subscriptionConfig.eventTopic).isEqualTo("topic")
    }

    @Test
    fun `groupName return the correct value`() {
        assertThat(subscription.subscriptionConfig.groupName).isEqualTo("group")
    }
}
