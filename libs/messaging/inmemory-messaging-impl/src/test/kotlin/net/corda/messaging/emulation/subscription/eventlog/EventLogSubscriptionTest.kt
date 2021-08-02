package net.corda.messaging.emulation.subscription.eventlog

import io.mockk.mockk
import io.mockk.verify
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EventLogSubscriptionTest {
    private val config = SubscriptionConfig("group", "topic")
    private val thread = mockk<EventLogSubscriptionThread<String, Long>>(relaxed = true)
    private val subscription = EventLogSubscription<String, Long>(
        config,
        mockk(relaxed = true),
        null,
        mockk(relaxed = true) ,
        {thread}
    )

    @Test
    fun `start will start the thread`() {
        subscription.start()

        verify {
            thread.start()
        }
    }

    @Test
    fun `start will not start two thread`() {
        subscription.start()
        subscription.start()
        subscription.start()

        verify(exactly = 1) {
            thread.start()
        }
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

        verify {
            thread.stop()
        }
    }

    @Test
    fun `stop will not stop the thread if it was not started`() {
        subscription.stop()

        verify(exactly = 0) {
            thread.stop()
        }
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
