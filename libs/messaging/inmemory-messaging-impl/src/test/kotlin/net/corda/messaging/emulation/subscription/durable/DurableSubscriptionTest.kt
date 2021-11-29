package net.corda.messaging.emulation.subscription.durable

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
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

class DurableSubscriptionTest {
    private val subscriptionConfig = SubscriptionConfig("group", "topic")
    private val returnedRecords = mutableListOf<Record<*, *>>()
    private val receivedRecords = mutableListOf<Record<String, String>>()
    private val processor = object : DurableProcessor<String, String> {
        override fun onNext(events: List<Record<String, String>>): List<Record<*, *>> {
            receivedRecords.addAll(events)
            return returnedRecords
        }

        override val keyClass = String::class.java
        override val valueClass = String::class.java
    }
    private val runningConsumption = mock<Consumption> {
        on { isRunning } doReturn true
    }
    private val topicService = mock<TopicService> {
        on { createConsumption(any()) } doReturn runningConsumption
    }
    private val lifecycleCoordinator: LifecycleCoordinator = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn lifecycleCoordinator
    }
    private val subscription =
        DurableSubscription(subscriptionConfig, processor, null, topicService, lifecycleCoordinatorFactory)

    @Test
    fun `first start will start a consumption`() {
        subscription.start()

        verify(topicService).createConsumption(isA<DurableConsumer<String, String>>())
    }

    @Test
    fun `second start will start a consumption only once`() {
        subscription.start()
        subscription.start()

        verify(topicService, times(1)).createConsumption(isA<DurableConsumer<String, String>>())
    }

    @Test
    fun `isRunning will return false if not started`() {
        assertThat(subscription.isRunning).isFalse
    }

    @Test
    fun `isRunning will return true is consumption running`() {
        subscription.start()

        assertThat(subscription.isRunning).isTrue
    }

    @Test
    fun `stop will stop any running consumption`() {
        subscription.start()

        subscription.stop()

        verify(runningConsumption).stop()
    }

    @Test
    fun `second stop will stop only once`() {
        subscription.start()

        subscription.stop()
        subscription.stop()

        verify(runningConsumption, times(1)).stop()
    }

    @Test
    fun `processRecords will send the correct record forward`() {
        val records = listOf(
            RecordMetadata(1L, Record("topic", "key", 2), 2),
            RecordMetadata(1L, Record("topic", 1, "value"), 2),
            RecordMetadata(1L, Record("topic", "key", "value1"), 2),
            RecordMetadata(1L, Record("topic", "key", "value2"), 2),
        )

        subscription.processRecords(records)

        assertThat(receivedRecords).containsExactly(
            Record("topic", "key", "value1"),
            Record("topic", "key", "value2"),
        )
    }

    @Test
    fun `processRecords will publish any results`() {
        returnedRecords.add(Record("topic2", 1, "value"))
        subscription.processRecords(emptyList())

        verify(topicService).addRecords(listOf(Record("topic2", 1, "value")))
    }
}
