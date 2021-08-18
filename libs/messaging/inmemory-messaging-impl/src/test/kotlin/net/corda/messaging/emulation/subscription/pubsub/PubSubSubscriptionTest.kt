package net.corda.messaging.emulation.subscription.pubsub

import com.typesafe.config.Config
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory.Companion.EVENT_TOPIC
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory.Companion.GROUP_NAME
import net.corda.messaging.emulation.topic.model.Consumption
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.messaging.emulation.topic.service.TopicService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

class PubSubSubscriptionTest {
    private val config = mock<Config> {
        on { getString(EVENT_TOPIC) } doReturn "topic"
        on { getString(GROUP_NAME) } doReturn "group"
    }
    private val processor = mock<PubSubProcessor<String, Number>> {
        on { keyClass } doReturn String::class.java
        on { valueClass } doReturn Number::class.java
    }
    private val executor = mock<ExecutorService>()
    private val consumeLifeCycle = mock<Consumption>()
    private val topicService = mock<TopicService> {
        on { subscribe(any()) } doReturn consumeLifeCycle
    }

    private val pubSubSubscription = PubSubSubscription(config, processor, executor, topicService)

    @Test
    fun `verify topic name is correct`() {
        assertThat(pubSubSubscription.topic).isEqualTo("topic")
    }

    @Test
    fun `verify group name is correct`() {
        assertThat(pubSubSubscription.groupName).isEqualTo("group")
    }

    @Test
    fun `isRunning return false if was not started`() {
        assertThat(pubSubSubscription.isRunning).isFalse
    }

    @Test
    fun `isRunning return false if thread had died`() {
        doReturn(false).whenever(consumeLifeCycle).isRunning
        pubSubSubscription.start()

        assertThat(pubSubSubscription.isRunning).isFalse
    }

    @Test
    fun `isRunning return true if thread is running`() {
        doReturn(true).whenever(consumeLifeCycle).isRunning
        pubSubSubscription.start()

        assertThat(pubSubSubscription.isRunning).isTrue
    }

    @Test
    fun `start will subscribe a consumer`() {
        pubSubSubscription.start()

        verify(topicService).subscribe(any())
    }

    @Test
    fun `double start will subscribe a consumer only once`() {
        pubSubSubscription.start()
        pubSubSubscription.start()

        verify(topicService, times(1)).subscribe(any())
    }

    @Test
    fun `stop will stop any running thread`() {
        pubSubSubscription.start()
        pubSubSubscription.stop()

        verify(consumeLifeCycle).stop()
    }

    @Test
    fun `stop will stop any running thread only once`() {
        pubSubSubscription.start()
        pubSubSubscription.stop()
        pubSubSubscription.stop()

        verify(consumeLifeCycle, times(1)).stop()
    }

    @Test
    fun `processRecords submit to executor`() {
        val future = mock<Future<Any>>()
        val captor = argumentCaptor<Runnable>()
        doReturn(future).whenever(executor).submit(captor.capture())
        val record = Record<String, Number>("topic", "key6", 4)
        val records = listOf(
            RecordMetadata(
                offset = 1,
                partition = 1,
                record = record
            )
        )

        pubSubSubscription.processRecords(records)
        verify(processor, never()).onNext(any())

        captor.firstValue.run()
        verify(processor).onNext(record)
    }

    @Test
    fun `processRecords send to processor in no executor`() {
        val subscription = PubSubSubscription(config, processor, null, topicService)
        val record = Record<String, Number>("topic", "key6", 3)
        val records = listOf(
            RecordMetadata(
                offset = 1,
                partition = 1,
                record = record
            )
        )

        subscription.processRecords(records)

        verify(processor).onNext(record)
    }

    @Test
    fun `processRecords ignore invalid keys and values`() {
        val subscription = PubSubSubscription(config, processor, null, topicService)
        val record1 = Record("topic", "key6", "3")
        val record2 = Record<Int, Number>("topic", 4, 4)
        val record3 = Record<String, Number>("topic", "key6", null)
        val records = listOf(
            RecordMetadata(
                offset = 1,
                partition = 1,
                record = record1
            ),
            RecordMetadata(
                offset = 2,
                partition = 2,
                record = record2
            ),
            RecordMetadata(
                offset = 2,
                partition = 2,
                record = record3
            ),
        )

        subscription.processRecords(records)

        verify(processor).onNext(record3)
    }
}
