package net.corda.messaging.subscription

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.constants.SubscriptionType
import net.corda.messaging.createResolvedSubscriptionConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.Logger

class EventSourceSubscriptionImplTest {
    private val config = createResolvedSubscriptionConfig(SubscriptionType.EVENT_SOURCE)
    private val eventSourceConsumer = mock<EventSourceConsumer<String, Int>>()
    private val threadLooper = mock<ThreadLooper>()
    private var looperCallback: () -> Unit = {}
    private val threadLooperFactory: (String, () -> Unit) -> ThreadLooper = { _, callback ->
        looperCallback = callback
        threadLooper
    }
    private val logger = mock<Logger>()

    private val target = EventSourceSubscriptionImpl(
        config,
        eventSourceConsumer,
        threadLooperFactory,
        logger
    )

    @Test
    fun `return is running state when thread is running in the background`(){
        whenever(threadLooper.isRunning).thenReturn(true)
        assertThat(target.isRunning).isTrue
    }

    @Test
    fun `subscription name`(){
        val name = LifecycleCoordinatorName("a","b")
        whenever(threadLooper.lifecycleCoordinatorName).thenReturn(name)
        assertThat(target.subscriptionName).isEqualTo(name)
    }

    @Test
    fun `start starts background thread`(){
        target.start()
        verify(threadLooper).start()
    }

    @Test
    fun `stop stops background thread`(){
        target.close()
        verify(threadLooper).close()
    }

    @Test
    fun `Background thread runs poll loop`(){
        // call the polling loop
        looperCallback()
        verify(eventSourceConsumer).poll()
    }
}