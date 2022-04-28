package net.corda.messaging.emulation.subscription.stateandevent

import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.emulation.topic.model.Consumption
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.messaging.emulation.topic.service.TopicService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EventSubscriptionTest {
    private val runningConsumption = mock<Consumption> {
        on { isRunning } doReturn true
    }
    private val topicService = mock<TopicService> {
        on { createConsumption(any()) } doReturn runningConsumption
    }
    private val response = mutableListOf<Record<*, *>>()
    private var newState: String? = null
    private val stateSubscription = mock<StateSubscription<String, String>>()
    private val received = mutableListOf<Pair<String?, Record<String, String>>>()
    private val subscription = mock<InMemoryStateAndEventSubscription<String, String, String>> {
        on { topicService } doReturn topicService
        on { subscriptionConfig } doReturn SubscriptionConfig("group", "topic")
        on { stateSubscriptionConfig } doReturn SubscriptionConfig("group1", "topic1")
        on { stateSubscription } doReturn stateSubscription
        on { processor } doReturn object : StateAndEventProcessor<String, String, String> {
            override fun onNext(state: String?, event: Record<String, String>): StateAndEventProcessor.Response<String> {
                received.add(state to event)
                return StateAndEventProcessor.Response(newState, response)
            }

            override val keyClass = String::class.java
            override val stateValueClass = String::class.java
            override val eventValueClass = String::class.java
        }
    }
    private val eventsSubscription = EventSubscription(subscription)

    @Test
    fun `start will create a new consumption`() {
        eventsSubscription.start()

        verify(topicService).createConsumption(isA<EventConsumer<String, String>>())
    }

    @Test
    fun `second start will create a new consumption only once`() {
        eventsSubscription.start()
        eventsSubscription.start()

        verify(topicService, times(1)).createConsumption(isA<EventConsumer<String, String>>())
    }

    @Test
    fun `isRunning will return false if not started`() {
        assertThat(eventsSubscription.isRunning).isFalse
    }

    @Test
    fun `isRunning will return consumption state if running`() {
        eventsSubscription.start()

        assertThat(eventsSubscription.isRunning).isTrue
    }

    @Test
    fun `stop will stop the consumption`() {
        eventsSubscription.start()

        eventsSubscription.stop()

        verify(runningConsumption).stop()
    }

    @Test
    fun `second stop will not stop the consumption again`() {
        eventsSubscription.start()

        eventsSubscription.stop()
        eventsSubscription.stop()

        verify(runningConsumption, times(1)).stop()
    }

    @Test
    fun `processEvents will ignore wrong event type`() {
        val records = listOf(
            RecordMetadata(2L, Record("topic", "key", 2), 1),
            RecordMetadata(2L, Record("topic", 1, "value"), 1),
        )

        eventsSubscription.processEvents(records)

        assertThat(received).isEmpty()
    }

    @Test
    fun `processEvents will send the correct state and event`() {
        val records = listOf(
            RecordMetadata(2L, Record("topic", "key1", "event1"), 1),
            RecordMetadata(2L, Record("topic", "key2", "event2"), 1),
        )
        whenever(stateSubscription.getValue("key1")).thenReturn("state1")

        eventsSubscription.processEvents(records)

        assertThat(received).containsExactly(
            "state1" to Record("topic", "key1", "event1"),
            null to Record("topic", "key2", "event2"),
        )
    }

    @Test
    fun `processEvents will update the state`() {
        newState = "hi"
        val records = listOf(
            RecordMetadata(2L, Record("topic", "key2", "event2"), 1),
        )

        eventsSubscription.processEvents(records)

        verify(subscription).setValue("key2", "hi", 1)
    }

    @Test
    fun `processEvents will publish new records`() {
        newState = "hi"
        response.add(Record("topic2", "key", "event"))
        val records = listOf(
            RecordMetadata(2L, Record("topic", "key2", "event2"), 1),
        )

        eventsSubscription.processEvents(records)

        verify(topicService).addRecords(
            listOf(
                Record("topic1", "key2", "hi"),
                Record("topic2", "key", "event"),
            )
        )
    }

    @Test
    fun `processEvents will wait for events before process them`() {
        val records = listOf(
            RecordMetadata(2L, Record("topic", "key2", "event2"), 1),
        )

        eventsSubscription.processEvents(records)

        verify(subscription.stateSubscription).waitForReady()
    }

    @Test
    fun `processEvents will send post commit notification`() {
        val listener = mock<StateAndEventListener<String, String>>()
        whenever(subscription.stateAndEventListener).doReturn(listener)
        newState = "hi"
        val records = listOf(
            RecordMetadata(2L, Record("topic", "key2", "event2"), 1),
        )

        eventsSubscription.processEvents(records)

        verify(listener).onPostCommit(mapOf("key2" to "hi"))
    }
}
