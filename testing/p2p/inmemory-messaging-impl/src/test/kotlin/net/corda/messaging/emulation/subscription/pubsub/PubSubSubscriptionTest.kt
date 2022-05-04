package net.corda.messaging.emulation.subscription.pubsub

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.emulation.topic.model.Consumption
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.messaging.emulation.topic.service.TopicService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.util.concurrent.CompletableFuture

class PubSubSubscriptionTest {
    private val config = SubscriptionConfig("group", "topic")
    private val processor = mock<PubSubProcessor<String, Number>> {
        on { keyClass } doReturn String::class.java
        on { valueClass } doReturn Number::class.java
        on { onNext(any()) } doReturn CompletableFuture.completedFuture(Unit)
    }
    private val consumeLifeCycle = mock<Consumption>()
    private val topicService = mock<TopicService> {
        on { createConsumption(any()) } doReturn consumeLifeCycle
    }
    private val lifecycleCoordinator: LifecycleCoordinator = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn lifecycleCoordinator
    }

    private val pubSubSubscription =
        PubSubSubscription(config, processor, topicService, lifecycleCoordinatorFactory, "0")

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

        verify(topicService).createConsumption(any())
    }

    @Test
    fun `double start will subscribe a consumer only once`() {
        pubSubSubscription.start()
        pubSubSubscription.start()

        verify(topicService, times(1)).createConsumption(any())
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
    fun `processRecords send to processor`() {
        val subscription = PubSubSubscription(config, processor, topicService, lifecycleCoordinatorFactory, "0")
        val record = Record<String, Number>("topic", "key6", 3)
        val records = listOf(
            RecordMetadata(
                offset = 1,
                partition = 1,
                record = record
            )
        )

        subscription.processRecords(records)
        subscription.processRecords(records)

        verify(processor, times(2)).onNext(record)
    }

    @Test
    fun `processRecords send to processor if ExecutionException`() {
        val future = CompletableFuture<Unit>()
        future.completeExceptionally(IOException(""))
        val exceptionProcessor = mock<PubSubProcessor<String, Number>> {
            on { keyClass } doReturn String::class.java
            on { valueClass } doReturn Number::class.java
            on { onNext(any()) } doReturn future
        }
        val subscription =
            PubSubSubscription(config, exceptionProcessor, topicService, lifecycleCoordinatorFactory, "0")
        val record = Record<String, Number>("topic", "key6", 3)
        val records = listOf(
            RecordMetadata(
                offset = 1,
                partition = 1,
                record = record
            )
        )

        subscription.processRecords(records)
        subscription.processRecords(records)
        verify(exceptionProcessor, times(2)).onNext(record)
    }

    @Test
    fun `processRecords ignore invalid keys and values`() {
        val subscription = PubSubSubscription(config, processor, topicService, lifecycleCoordinatorFactory, "0")
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
