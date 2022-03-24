package net.corda.messaging.emulation.publisher

import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

@Suppress("ClassNaming")
class CordaPublisherTest {
    private val topicService = mock<TopicService>()
    private val asyncConfig = PublisherConfig("clientId123", false)
    private val transactionalConfig = PublisherConfig("clientId123", true)

    private val topicName = "topic123"
    private val record = Record(topicName, "key1", ByteBuffer.wrap("value1".toByteArray()))
    private val cordaPublisher = CordaPublisher(asyncConfig, topicService)

    @Nested
    inner class `publish tests` {
        @Test
        fun `successful publish return valid futures`() {
            val futures = cordaPublisher.publish(listOf(record, record, record))
            assertThat(futures).hasSize(3).allSatisfy {
                assertThat(it).isCompleted
            }
        }

        @Test
        fun `publish sends records to topic`() {
            cordaPublisher.publish(listOf(record, record, record))

            verify(topicService, times(1)).addRecords(listOf(record, record, record))
        }
        @Test
        fun `successful publish with transaction will return one valid future`() {
            val publisher = CordaPublisher(transactionalConfig, topicService)

            val futures = publisher.publish(listOf(record, record, record))
            assertThat(futures).hasSize(1).allSatisfy {
                assertThat(it).isCompleted
            }
        }

        @Test
        fun `failed publish return exceptional futures with transaction`() {
            val publisher = CordaPublisher(transactionalConfig, topicService)
            doThrow(CordaRuntimeException("")).whenever(topicService).addRecords(any())
            val futures = publisher.publish(listOf(record, record, record))
            assertThat(futures).hasSize(1).allSatisfy {
                assertThat(it)
                    .hasFailedWithThrowableThat()
                    .isInstanceOf(CordaMessageAPIFatalException::class.java)
            }
        }

        @Test
        fun `failed publish return exceptional futures`() {
            doThrow(CordaRuntimeException("")).whenever(topicService).addRecords(any())
            val futures = cordaPublisher.publish(listOf(record, record, record))
            assertThat(futures).hasSize(3).allSatisfy {
                assertThat(it)
                    .hasFailedWithThrowableThat()
                    .isInstanceOf(CordaMessageAPIFatalException::class.java)
            }
        }
    }

    @Nested
    inner class `publishToPartition tests` {
        @Test
        fun `successful publishToPartition return valid futures`() {
            val futures = cordaPublisher.publishToPartition(listOf(1 to record, 2 to record, 1 to record))
            assertThat(futures).hasSize(3).allSatisfy {
                assertThat(it).isCompleted
            }
        }

        @Test
        fun `publishToPartition sends records to topic with partitions`() {
            cordaPublisher.publishToPartition(listOf(1 to record, 2 to record, 1 to record))

            verify(topicService, times(1))
                .addRecordsToPartition(listOf(record, record), 1)
            verify(topicService, times(1))
                .addRecordsToPartition(listOf(record), 2)
        }
        @Test
        fun `successful publishToPartition with transaction will return one valid future`() {
            val publisher = CordaPublisher(transactionalConfig, topicService)

            val futures = publisher.publishToPartition(listOf(1 to record, 2 to record, 1 to record))
            assertThat(futures).hasSize(1).allSatisfy {
                assertThat(it).isCompleted
            }
        }

        @Test
        fun `failed publishToPartition return exceptional futures with transaction`() {
            val publisher = CordaPublisher(transactionalConfig, topicService)
            doThrow(CordaRuntimeException("")).whenever(topicService).addRecordsToPartition(any(), any())
            val futures = publisher.publishToPartition(listOf(1 to record, 2 to record, 1 to record))
            assertThat(futures).hasSize(1).allSatisfy {
                assertThat(it)
                    .hasFailedWithThrowableThat()
                    .isInstanceOf(CordaMessageAPIFatalException::class.java)
            }
        }

        @Test
        fun `failed publishToPartition return exceptional futures`() {
            doThrow(CordaRuntimeException("")).whenever(topicService).addRecordsToPartition(any(), any())
            val futures = cordaPublisher.publishToPartition(listOf(1 to record, 2 to record, 1 to record))
            assertThat(futures).hasSize(3).allSatisfy {
                assertThat(it)
                    .hasFailedWithThrowableThat()
                    .isInstanceOf(CordaMessageAPIFatalException::class.java)
            }
        }
    }

    @Test
    fun testClose() {
        cordaPublisher.close()
    }
}
