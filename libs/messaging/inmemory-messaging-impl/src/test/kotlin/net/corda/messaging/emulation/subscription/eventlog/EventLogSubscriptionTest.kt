package net.corda.messaging.emulation.subscription.eventlog

import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class EventLogSubscriptionTest {
    private val config = mock<InMemoryEventLogSubscriptionConfig> {
        on { subscriptionConfig } doReturn SubscriptionConfig("group", "topic")
    }
    private val thread = mock<EventLogSubscriptionMainLoop<String, Long>>()
    private val subscription = EventLogSubscription<String, Long>(
        config,
        mock(),
        null,
        mock(),
        { thread }
    )

    @Test
    fun `start will start the thread`() {
        subscription.start()

        verify(thread).start()
    }

    @Test
    fun `start will not start two thread`() {
        subscription.start()
        subscription.start()
        subscription.start()

        verify(thread, times(1)).start()
    }

    @Test
    fun `isRunning will return true if the thread was started`() {
        subscription.start()

        assertThat(subscription.isRunning).isTrue
    }

    @Test
    fun `isRunning will return false if the thread was not started`() {
        assertThat(subscription.isRunning).isFalse
    }

    @Test
    fun `isRunning will return false if the thread was not stopped`() {
        subscription.start()
        subscription.stop()

        assertThat(subscription.isRunning).isFalse
    }

    @Test
    fun `stop will stop the thread`() {
        subscription.start()
        subscription.stop()

        verify(thread).stop()
    }

    @Test
    fun `stop will not stop the thread if it was not started`() {
        subscription.stop()

        verify(thread, never()).stop()
    }

    @Test
    fun `topic return the correct topic`() {
        assertThat(subscription.topic).isEqualTo("topic")
    }

    @Test
    fun `group return the correct topic`() {
        assertThat(subscription.group).isEqualTo("group")
    }
}
