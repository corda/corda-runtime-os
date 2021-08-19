package net.corda.messaging.emulation.subscription.compacted

import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.emulation.topic.model.Consumption
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.messaging.emulation.topic.service.TopicService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
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
    private val recordsToSend = mutableListOf<RecordMetadata>()
    private val consumption = mock<Consumption>()
    private val topic = mock<TopicService> {
        on { handleAllRecords(any(), any()) } doAnswer {
            val handler = it.getArgument<(Sequence<RecordMetadata>) -> Unit>(1)
            handler(recordsToSend.asSequence())
        }
        on { subscribe(any()) } doReturn consumption
    }

    private val subscription = InMemoryCompactedSubscription(
        subscriptionConfig,
        processor,
        topic
    )

    @Test
    fun `topicName return the correct topicName`() {
        assertThat(subscription.topicName).isEqualTo("topic")
    }

    @Test
    fun `groupName return the correct groupName`() {
        assertThat(subscription.groupName).isEqualTo("group")
    }

    @Test
    fun `updateSnapshots will use the latest snapshot for each key`() {
        recordsToSend.addAll(
            listOf(
                RecordMetadata(
                    offset = 1,
                    partition = 2,
                    record = Record("topic", "key", URL("https://www.r3.com"))
                ),
                RecordMetadata(
                    offset = 2,
                    partition = 2,
                    record = Record("topic", "key", URL("http://www.corda.net"))
                ),
                RecordMetadata(
                    offset = 3,
                    partition = 2,
                    record = Record("topic", "key", URL("https://www.corda.net"))
                ),
            )
        )

        subscription.updateSnapshots()

        assertThat(subscription.getValue("key").toString()).isEqualTo("https://www.corda.net")
    }

    @Test
    fun `updateSnapshots will remove invalid values`() {
        recordsToSend.add(
            RecordMetadata(
                offset = 3,
                partition = 2,
                record = Record("topic", "key", "string")
            ),
        )

        subscription.updateSnapshots()

        assertThat(subscription.getValue("key")).isNull()
    }

    @Test
    fun `updateSnapshots will remove invalid keys`() {
        recordsToSend.add(
            RecordMetadata(
                offset = 3,
                partition = 2,
                record = Record("topic", 12, URL("https://www.corda.net"))
            ),
        )

        subscription.updateSnapshots()

        assertThat(subscription.getValue("12")).isNull()
    }

    @Test
    fun `updateSnapshots will clear the data the second time`() {
        recordsToSend.addAll(
            listOf(
                RecordMetadata(
                    offset = 1,
                    partition = 2,
                    record = Record("topic", "key", URL("https://www.r3.com"))
                ),
                RecordMetadata(
                    offset = 2,
                    partition = 2,
                    record = Record("topic", "key", URL("http://www.corda.net"))
                ),
                RecordMetadata(
                    offset = 3,
                    partition = 2,
                    record = Record("topic", "key", URL("https://www.corda.net"))
                ),
            )
        )
        subscription.updateSnapshots()
        recordsToSend.clear()

        subscription.updateSnapshots()

        assertThat(subscription.getValue("key")).isNull()
    }

    @Test
    fun `onNewRecord will send the correct data to the processor`() {
        recordsToSend.addAll(
            listOf(
                RecordMetadata(
                    offset = 3,
                    partition = 2,
                    record = Record("topic", "key1", URL("https://www.corda.net"))
                ),
                RecordMetadata(
                    offset = 4,
                    partition = 10,
                    record = Record("topic", "key2", URL("https://www.r3.com"))
                ),
            )
        )
        subscription.updateSnapshots()

        subscription.onNewRecord(Record("topic", "key1", URL("https://github.com/corda/")))

        verify(processor).onNext(
            Record(
                "topic",
                "key1",
                URL("https://github.com/corda/")
            ),
            URL("https://www.corda.net"),
            mapOf(
                "key1" to URL("https://github.com/corda/"),
                "key2" to URL("https://www.r3.com")
            )
        )
    }

    @Test
    fun `onNewRecord will remove the data if value is null`() {
        recordsToSend.addAll(
            listOf(
                RecordMetadata(
                    offset = 3,
                    partition = 2,
                    record = Record("topic", "key1", URL("https://www.corda.net"))
                ),
                RecordMetadata(
                    offset = 4,
                    partition = 10,
                    record = Record("topic", "key2", URL("https://www.r3.com"))
                ),
            )
        )
        subscription.updateSnapshots()

        subscription.onNewRecord(Record("topic", "key1", null))

        verify(processor).onNext(
            Record(
                "topic",
                "key1",
                null
            ),
            URL("https://www.corda.net"),
            mapOf(
                "key2" to URL("https://www.r3.com")
            )
        )
    }

    @Test
    fun `updateSnapshots will send the snapshots`() {
        recordsToSend.addAll(
            listOf(
                RecordMetadata(
                    offset = 3,
                    partition = 2,
                    record = Record("topic", "key1", URL("https://www.corda.net"))
                ),
                RecordMetadata(
                    offset = 4,
                    partition = 10,
                    record = Record("topic", "key2", URL("https://www.r3.com"))
                ),
                RecordMetadata(
                    offset = 4,
                    partition = 10,
                    record = Record("topic", "key3", null)
                ),
            )
        )

        subscription.updateSnapshots()

        verify(processor).onSnapshot(
            mapOf(
                "key1" to URL("https://www.corda.net"),
                "key2" to URL("https://www.r3.com")
            )
        )
    }

    @Test
    fun `start will subscribe a consumer`() {
        subscription.start()

        verify(topic).subscribe(any<CompactedConsumer<String, URL>>())
    }

    @Test
    fun `second start will subscribe a consumer only once`() {
        subscription.start()
        subscription.start()
        subscription.start()

        verify(topic, times(1)).subscribe(any())
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
