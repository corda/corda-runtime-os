package net.corda.messaging.emulation.subscription.eventlog

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class EventLogSubscriptionThreadTest {
    private val eventsSent = slot< List<EventLogRecord<String, SubscriptionConfig>>>()
    private val eventLogSubscription = mockk<EventLogSubscription<String, SubscriptionConfig>>(relaxed = true) {
        every { topic } returns "topic"
        every { group } returns "group"
        every { processor.valueClass } returns SubscriptionConfig::class.java
        every { processor.keyClass } returns String::class.java
        every { processor.onNext(capture(eventsSent)) } returns emptyList()
    }
    private val thread = mockk<Thread>(relaxed = true)
    private val testObject = EventLogSubscriptionThread(eventLogSubscription, { thread })

    @Test
    fun `start set the thread properties and start it`() {
        testObject.start()

        verify {
            thread.name = any()
            thread.isDaemon = true
            thread.priority = -1
            thread.start()
        }
    }

    @Test
    fun `stop will join the thread`() {
        testObject.stop()

        verify {
            thread.join()
        }
    }

    @Test
    fun `run will subscribe to the correct topic`() {
        testObject.stop()
        testObject.run()

        verify {
            eventLogSubscription.topicService.subscribe("topic", "group", OffsetStrategy.LATEST)
        }
    }

    @Test
    fun `run will stop once stop is called`() {
        val counter = AtomicInteger()
        every {
            eventLogSubscription.topicService.getRecords(
                any(),
                any(),
                any()
            )
        } answers {
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
        every {
            eventLogSubscription.topicService.getRecords(
                any(),
                any(),
                any()
            )
        } answers {
            testObject.stop()
            emptyList()
        }

        testObject.run()

        verify {
            eventLogSubscription.topicService.getRecords("topic", "group", any())
        }
    }

    @Test
    fun `run will send the correct record to the processor`() {
        val value = SubscriptionConfig("t1", "g1")
        every { eventLogSubscription.partitioner(any()) } returns 32
        every {
            eventLogSubscription.topicService.getRecords(
                any(),
                any(),
                any()
            )
        } answers {
            testObject.stop()
            listOf(
                mockk {
                    every { record.value } returns "String"
                },
                mockk {
                    every { record.value } returns value
                    every { record.key } returns 33
                },
                mockk {
                    every { record.value } returns value
                    every { record.key } returns "key"
                    every { record.topic } returns "record-topic"
                    every { offset } returns 123
                },
                mockk {
                    every { record.value } returns null
                },
            )
        }

        testObject.run()

        assertThat(eventsSent.captured).isEqualTo(listOf(EventLogRecord("record-topic", "key", value, 32, 123)))
    }

    @Test
    fun `run will not send the emty list`() {
        every {
            eventLogSubscription.topicService.getRecords(
                any(),
                any(),
                any()
            )
        } answers {
            testObject.stop()
            listOf(
                mockk {
                    every { record.value } returns null
                },
                mockk {
                    every { record.value } returns null
                },
                mockk {
                    every { record.value } returns null
                },
            )
        }

        testObject.run()

        assertThat(eventsSent.isCaptured).isFalse
    }
}
