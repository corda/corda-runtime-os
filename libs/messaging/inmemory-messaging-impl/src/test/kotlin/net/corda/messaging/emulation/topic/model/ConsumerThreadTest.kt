package net.corda.messaging.emulation.topic.model

import net.corda.messaging.emulation.properties.SubscriptionConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

class ConsumerThreadTest {
    private val consumer = mock<Consumer>()
    private val topic = mock<Topic>()
    private val config = SubscriptionConfiguration(10, Duration.ofSeconds(1))
    private val thread = mock<Thread>()
    private val runnable = AtomicReference<Runnable>()

    private val consumerThread = ConsumerThread(consumer, topic, config) {
        runnable.set(it)
        thread
    }

    @Test
    fun `start will set the thread to daemon`() {
        consumerThread.start()

        verify(thread).isDaemon = true
    }

    @Test
    fun `start will subscribe in the thread`() {
        consumerThread.start()
        runnable.get().run()

        verify(topic).subscribe(consumer, config)
    }

    @Test
    fun `start will start the thread`() {
        consumerThread.start()

        verify(thread).start()
    }

    @Test
    fun `stop will unsubscribe`() {
        consumerThread.stop()

        verify(topic).unsubscribe(consumer)
    }

    @Test
    fun `stop will join the thread`() {
        consumerThread.stop()

        verify(thread).join(1000L)
    }

    @Test
    fun `isRunning will return the thread state`() {
        assertThat(consumerThread.isRunning).isFalse
    }
}
