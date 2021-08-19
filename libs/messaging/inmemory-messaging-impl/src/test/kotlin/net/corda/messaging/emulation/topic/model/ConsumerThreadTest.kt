package net.corda.messaging.emulation.topic.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ConsumerThreadTest {
    private val thread = mock<Thread>()
    private val runnable = AtomicReference<Runnable>()
    private val killed = AtomicBoolean(false)
    private val loop = mock<Runnable>()

    private val consumerThread = ConsumptionThread(
        "name",
        Duration.ofSeconds(1),
        { killed.set(true) },
        loop,
        {
            runnable.set(it)
            thread
        }
    )

    @Test
    fun `start will set the thread to daemon`() {
        consumerThread.start()

        verify(thread).isDaemon = true
    }

    @Test
    fun `start will set the thread to run the loop`() {
        consumerThread.start()

        assertThat(runnable).hasValue(loop)
    }

    @Test
    fun `start will start the thread`() {
        consumerThread.start()

        verify(thread).start()
    }

    @Test
    fun `stop will kill the process`() {
        consumerThread.stop()

        assertThat(killed).isTrue
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
