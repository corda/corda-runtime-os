package net.corda.messaging.emulation.subscription.eventlog

import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.messaging.emulation.topic.service.TopicService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class EventLogSubscriptionMainLoopTest {
    private val eventsSent = argumentCaptor<List<EventLogRecord<String, SubscriptionConfig>>>()
    private val processor = mock<EventLogProcessor<String, SubscriptionConfig>> {
        on { keyClass } doReturn String::class.java
        on { valueClass } doReturn SubscriptionConfig::class.java
        on { onNext(eventsSent.capture()) } doReturn emptyList()
    }
    private val topicService = mock<TopicService>()
    private val config = mock<InMemoryEventLogSubscriptionConfig>()
    private val partitioner = mock<Partitioner>()
    private val subscribed = AtomicBoolean(false)
    private val eventLogSubscription = mock<EventLogSubscription<String, SubscriptionConfig>> {
        on { topic } doReturn "topic"
        on { group } doReturn "group"
        on { processor } doReturn processor
        on { topicService } doReturn topicService
        on { config } doReturn config
        on { partitioner } doReturn partitioner
        on { subscribedToTopic } doReturn subscribed
    }
    private val thread = mock<Thread>()
    private val testObject = EventLogSubscriptionMainLoop(eventLogSubscription, { thread })

    @Test
    fun `start set the thread properties and start it`() {
        testObject.start()

        verify(thread).name = any()
        verify(thread).isDaemon = true
        verify(thread).start()
    }

    @Test
    fun `stop will join the thread`() {
        testObject.stop()

        verify(thread).join()
    }

    @Test
    fun `run will subscribe to the correct topic`() {
        testObject.stop()
        testObject.run()

        verify(topicService).subscribe("topic", "group", OffsetStrategy.EARLIEST)
    }

    @Test
    fun `run will not subscribe twice`() {
        testObject.stop()
        subscribed.set(true)
        testObject.run()

        verify(topicService, never()).subscribe("topic", "group", OffsetStrategy.EARLIEST)
    }

    @Test
    fun `run will stop once stop is called`() {
        val counter = AtomicInteger()
        whenever(topicService.getRecords(any(), any(), any(), any()))
            .doAnswer {
                if (counter.incrementAndGet() >= 10) {
                    testObject.stop()
                }
                emptyList()
            }

        testObject.run()

        assertThat(counter.get()).isEqualTo(10)
    }

    @Test
    fun `run will get the correct records`() {
        whenever(topicService.getRecords(any(), any(), any(), any()))
            .doAnswer {
                testObject.stop()
                emptyList()
            }

        testObject.run()

        verify(topicService).getRecords(eq("topic"), eq("group"), any(), any())
    }

    @Test
    fun `run will send the correct record to the processor`() {
        val value = SubscriptionConfig("t1", "g1")
        whenever(partitioner(any())).doReturn(32)
        whenever(
            topicService.getRecords(
                any(),
                any(),
                any(),
                any()
            )
        ).doAnswer {
            testObject.stop()
            listOf<Record<Any, Any>>(
                mock {
                    on { this@on.value } doReturn "String"
                },
                mock {
                    on { this@on.value } doReturn value
                    on { this.key } doReturn 33
                },
                mock {
                    on { this@on.value } doReturn value
                    on { this.key } doReturn "key"
                    on { this.topic } doReturn "record-topic"
                },
                mock {
                    on { this@on.value } doReturn null
                },
            ).map {
                RecordMetadata(offset = 123, record = it)
            }
        }

        testObject.run()

        assertThat(eventsSent.firstValue)
            .isEqualTo(
                listOf(
                    EventLogRecord(
                        "record-topic",
                        "key",
                        value,
                        32,
                        123
                    )
                )
            )
    }

    @Test
    fun `run will not send the empty list`() {
        whenever(
            topicService.getRecords(
                any(),
                any(),
                any(),
                any()
            )
        ).doAnswer {
            testObject.stop()
            (1..3).map {
                RecordMetadata(offset = it.toLong(), record = mock<Record<String, SubscriptionConfig>>())
            }
        }

        testObject.run()

        assertThat(eventsSent.allValues).isEmpty()
    }
}
