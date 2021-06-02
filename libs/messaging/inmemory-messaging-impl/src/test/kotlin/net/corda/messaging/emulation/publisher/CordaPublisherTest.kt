package net.corda.messaging.emulation.publisher

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory.Companion.PUBLISHER_CLIENT_ID
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory.Companion.PUBLISHER_INSTANCE_ID
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory.Companion.PUBLISHER_TOPIC
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class CordaPublisherTest {
    private lateinit var cordaPublisher : CordaPublisher<String, ByteBuffer>
    private lateinit var topicService: TopicService
    private lateinit var config: Config
    private val topicName = "topic123"
    private val record = Record(topicName, "key1", ByteBuffer.wrap("value1".toByteArray()))

    @BeforeEach
    fun beforeEach() {
        topicService = mock()
        config = ConfigFactory.empty().withValue(PUBLISHER_TOPIC, ConfigValueFactory.fromAnyRef(topicName))
            .withValue(PUBLISHER_CLIENT_ID, ConfigValueFactory.fromAnyRef("clientId123"))
        cordaPublisher = CordaPublisher(config, topicService)
    }

    @Test
    fun testPublish() {
        val futures = cordaPublisher.publish(listOf(record, record, record))
        verify(topicService, times(1)).addRecords(any())
        assertThat(futures.size).isEqualTo(3)
        for (future in futures) {
            future.getOrThrow()
        }
    }

    @Test
    fun testPublishTransaction() {
        config = config.withValue(PUBLISHER_INSTANCE_ID, ConfigValueFactory.fromAnyRef(1))
        cordaPublisher = CordaPublisher(config, topicService)
        val futures = cordaPublisher.publish(listOf(record, record, record))
        verify(topicService, times(1)).addRecords(any())
        assertThat(futures.size).isEqualTo(1)
        for (future in futures) {
            future.getOrThrow()
        }
    }

    @Test
    fun testPublishFail() {
        doThrow(CordaRuntimeException("")).whenever(topicService).addRecords(any())
        val futures = cordaPublisher.publish(listOf(record, record, record))
        verify(topicService, times(1)).addRecords(any())
        assertThat(futures.size).isEqualTo(3)
        for (future in futures) {
            assertThrows(CordaMessageAPIFatalException::class.java) { future.getOrThrow() }
        }
    }

    @Test
    fun testPublishTransactionFail() {
        config = config.withValue(PUBLISHER_INSTANCE_ID, ConfigValueFactory.fromAnyRef(1))
        cordaPublisher = CordaPublisher(config, topicService)
        doThrow(CordaRuntimeException("")).whenever(topicService).addRecords(any())
        val futures = cordaPublisher.publish(listOf(record, record, record))
        verify(topicService, times(1)).addRecords(any())
        assertThat(futures.size).isEqualTo(1)
        for (future in futures) {
            assertThrows(CordaMessageAPIFatalException::class.java) { future.getOrThrow() }
        }
    }

    @Test
    fun testClose() {
        cordaPublisher.close()
    }
}
