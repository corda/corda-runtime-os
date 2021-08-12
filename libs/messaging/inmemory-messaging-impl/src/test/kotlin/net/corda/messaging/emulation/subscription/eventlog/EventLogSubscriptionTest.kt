package net.corda.messaging.emulation.subscription.eventlog

import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
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
    private val consumeLifeCycle = mock<Lifecycle>()
    private val topic = mock<TopicService> {
        on { subscribe(any()) } doReturn consumeLifeCycle
    }
    private val config = SubscriptionConfig(eventTopic = "topic", groupName = "group")
    private val subscription = EventLogSubscription<String, SubscriptionConfig>(
        subscriptionConfig = config,
        processor = mock(),
        partitionAssignmentListener = null,
        topicService = topic,
    )

    @Test
    fun `start will subscribe a consumer`() {
        subscription.start()

        verify(topic).subscribe(any())
    }

    @Test
    fun `double start will subscribe a consumer only once`() {
        subscription.start()
        subscription.start()

        verify(topic, times(1)).subscribe(any())
    }

    @Test
    fun `stop will stop the lifecycle`() {
        subscription.start()
        subscription.stop()

        verify(consumeLifeCycle).stop()
    }

    @Test
    fun `second stop will stop the lifecycle only once`() {
        subscription.start()
        subscription.stop()
        subscription.stop()

        verify(consumeLifeCycle, times(1)).stop()
    }

    @Test
    fun `isRunning will return false if had not started`() {

        assertThat(subscription.isRunning).isFalse
    }

    @Test
    fun `isRunning will return false if had thread was killed`() {
        subscription.start()
        whenever(consumeLifeCycle.isRunning).doReturn(false)

        assertThat(subscription.isRunning).isFalse
    }

    @Test
    fun `isRunning will return true if had thread is alive`() {
        subscription.start()
        whenever(consumeLifeCycle.isRunning).doReturn(true)

        assertThat(subscription.isRunning).isTrue
    }

    @Test
    fun `topicName return the correct value`() {
        assertThat(subscription.topicName).isEqualTo("topic")
    }

    @Test
    fun `groupName return the correct value`() {
        assertThat(subscription.groupName).isEqualTo("group")
    }
}
