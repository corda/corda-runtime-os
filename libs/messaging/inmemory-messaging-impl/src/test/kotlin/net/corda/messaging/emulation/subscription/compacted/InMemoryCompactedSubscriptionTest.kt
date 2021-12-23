package net.corda.messaging.emulation.subscription.compacted

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.emulation.topic.model.Consumption
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.messaging.emulation.topic.service.TopicService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.URL

class InMemoryCompactedSubscriptionTest {
    private val subscriptionConfig = SubscriptionConfig("group", "topic")
    private val processor = mock<CompactedProcessor<String, URL>> {
        on { keyClass } doReturn String::class.java
        on { valueClass } doReturn URL::class.java
    }
    private val consumption = mock<Consumption>()
    private val topic = mock<TopicService> {
        on { createConsumption(any()) } doReturn consumption
        on { getLatestOffsets(any()) } doReturn mapOf(
            1 to 1,
            2 to 2,
            3 to -1
        )
    }

    private val lifecycleCoordinator: LifecycleCoordinator = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn lifecycleCoordinator
    }

    private val subscription = InMemoryCompactedSubscription(
        subscriptionConfig,
        processor,
        topic,
        lifecycleCoordinatorFactory,
        "0"
    )


    @Test
    fun `getValue throws exception if we are waiting for the snapshot`() {
        assertThrows<IllegalStateException> {
            subscription.getValue("hello")
        }
    }

    @Test
    fun `getValue returns value if snapshots are ready`() {
        subscription.onNewRecord(RecordMetadata(1, Record("topic", "hello", URL("http://www.r3.com/")), 1))
        subscription.onNewRecord(RecordMetadata(2, Record("topic", "helloTwo", URL("http://www.corda.net/")), 2))

        val value = subscription.getValue("hello")

        assertThat(value).isEqualTo(URL("http://www.r3.com/"))
    }

    @Test
    fun `onNewRecord will remove the value from the snapshot if it's null`() {
        subscription.onNewRecord(RecordMetadata(1, Record("topic", "hello", URL("http://www.r3.com/")), 1))
        subscription.onNewRecord(RecordMetadata(2, Record("topic", "helloTwo", URL("http://www.corda.net/")), 2))
        val snapshots = argumentCaptor<Map<String, URL>>()
        doNothing().whenever(processor).onNext(any(), eq(URL("http://www.r3.com/")), snapshots.capture())

        subscription.onNewRecord(RecordMetadata(2, Record("topic", "hello", null), 1))

        assertThat(snapshots.firstValue).hasSize(1)
    }

    @Test
    fun `onNewRecord will not invoke onSnapshot if the snapshot is not ready`() {
        subscription.onNewRecord(RecordMetadata(1, Record("topic", "hello", URL("http://www.r3.com/")), 1))

        verify(processor, never()).onSnapshot(any())
    }

    @Test
    fun `onNewRecord will invoke onSnapshot if the snapshot is ready`() {
        subscription.onNewRecord(RecordMetadata(1, Record("topic", "hello", URL("http://www.r3.com/")), 1))
        subscription.onNewRecord(RecordMetadata(2, Record("topic", "helloTwo", URL("http://www.corda.net/")), 2))

        verify(processor, times(1)).onSnapshot(
            mapOf(
                "hello" to URL("http://www.r3.com/"),
                "helloTwo" to URL("http://www.corda.net/")
            )
        )
    }

    @Test
    fun `onNewRecord will invoke onNext if the snapshot is ready`() {
        subscription.onNewRecord(RecordMetadata(1, Record("topic", "hello", URL("http://www.r3.com/")), 1))
        subscription.onNewRecord(RecordMetadata(2, Record("topic", "helloTwo", URL("http://www.corda.net/")), 2))

        subscription.onNewRecord(RecordMetadata(4, Record("topic", "three", URL("http://corda.net/")), 3))

        verify(processor, times(1)).onNext(
            Record("topic", "three", URL("http://corda.net/")),
            null,
            mapOf(
                "hello" to URL("http://www.r3.com/"),
                "helloTwo" to URL("http://www.corda.net/"),
                "three" to URL("http://corda.net/")
            )
        )
    }

    @Test
    fun `onNewRecord will not invoke onNext if the type is wrong`() {
        subscription.onNewRecord(RecordMetadata(1, Record("topic", "hello", URL("http://www.r3.com/")), 1))
        subscription.onNewRecord(RecordMetadata(2, Record("topic", "helloTwo", URL("http://www.corda.net/")), 2))

        subscription.onNewRecord(RecordMetadata(4, Record("topic", "three", 5), 3))

        verify(processor, never()).onNext(any(), any(), any())
    }

    @Test
    fun `onNewRecord will not invoke onSnapshot while waiting for the snapshot`() {
        subscription.onNewRecord(RecordMetadata(0, Record("topic", "hello", URL("http://www.r3.com/")), 1))
        subscription.onNewRecord(RecordMetadata(1, Record("topic", "hello", URL("http://www.r3.com/")), 1))
        subscription.onNewRecord(RecordMetadata(0, Record("topic", "helloTwo", URL("http://www.corda.net/")), 2))

        subscription.onNewRecord(RecordMetadata(1, Record("topic", "helloTwo", URL("http://www.corda.net/")), 2))

        verify(processor, never()).onSnapshot(any())
    }

    @Test
    fun `onNewRecord will invoke onSnapshot the record has the wrong type`() {
        subscription.onNewRecord(RecordMetadata(1, Record("topic", "hello", URL("http://www.r3.com/")), 1))
        subscription.onNewRecord(RecordMetadata(2, Record("topic", 12, URL("http://www.corda.net/")), 2))

        verify(processor, times(1)).onSnapshot(
            mapOf(
                "hello" to URL("http://www.r3.com/"),
            )
        )
    }

    @Test
    fun `onNewRecord will update the value from the snapshot if it is not null`() {
        subscription.onNewRecord(RecordMetadata(1, Record("topic", "hello", URL("http://www.r3.com/")), 1))
        subscription.onNewRecord(RecordMetadata(2, Record("topic", "helloTwo", URL("http://www.corda.net/")), 2))
        val snapshots = argumentCaptor<Map<String, URL>>()
        doNothing().whenever(processor).onNext(any(), eq(URL("http://www.r3.com/")), snapshots.capture())

        subscription.onNewRecord(RecordMetadata(2, Record("topic", "hello", URL("http://r3.com/")), 1))

        assertThat(snapshots.firstValue).hasSize(2).containsEntry("hello", URL("http://r3.com/"))
    }

    @Test
    fun `topicName return the correct topicName`() {
        assertThat(subscription.topicName).isEqualTo("topic")
    }

    @Test
    fun `groupName return the correct groupName`() {
        assertThat(subscription.groupName).isEqualTo("group")
    }

    @Test
    fun `start will subscribe a consumer`() {
        subscription.start()

        verify(topic).createConsumption(any<CompactedConsumer<String, URL>>())
    }

    @Test
    fun `start will not send snapshot if we don't have all the data`() {
        subscription.start()

        verify(processor, never()).onSnapshot(any())
    }

    @Test
    fun `start will send snapshot if we have all the data`() {
        val topic = mock<TopicService> {
            on { createConsumption(any()) } doReturn consumption
            on { getLatestOffsets(any()) } doReturn mapOf(
                1 to -1,
                2 to -1,
                3 to -1
            )
        }
        val subscription = InMemoryCompactedSubscription(
            subscriptionConfig,
            processor,
            topic,
            lifecycleCoordinatorFactory,
            "0"
        )
        subscription.start()

        verify(processor, times(1)).onSnapshot(emptyMap())
    }

    @Test
    fun `second start will subscribe a consumer only once`() {
        subscription.start()
        subscription.start()
        subscription.start()

        verify(topic, times(1)).createConsumption(any())
    }

    @Test
    fun `stop will stop the lifecycle`() {
        subscription.start()

        subscription.stop()

        verify(consumption).stop()
    }

    @Test
    fun `second stop will stop the lifecycle only once`() {
        subscription.start()

        subscription.stop()
        subscription.stop()
        subscription.stop()

        verify(consumption, times(1)).stop()
    }

    @Test
    fun `isRunning will return false after stop`() {
        subscription.start()
        subscription.stop()

        assertThat(subscription.isRunning).isFalse
    }

    @Test
    fun `isRunning will return true if subscription is alive`() {
        subscription.start()
        whenever(consumption.isRunning).doReturn(true)

        assertThat(subscription.isRunning).isTrue
    }

    @Test
    fun `isRunning will return false if subscription is not alive`() {
        subscription.start()
        whenever(consumption.isRunning).doReturn(false)

        assertThat(subscription.isRunning).isFalse
    }
}
