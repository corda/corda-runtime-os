package net.corda.messaging.emulation.publisher

import com.nhaarman.mockito_kotlin.*
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory.Companion.PUBLISHER_CLIENT_ID
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory.Companion.PUBLISHER_INSTANCE_ID
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

class CordaPublisherTest {
    private lateinit var cordaPublisher : CordaPublisher
    private lateinit var topicService: TopicService
    private lateinit var config: Config
    private val topicName = "topic123"
    private val record = Record(topicName, "key1", ByteBuffer.wrap("value1".toByteArray()))

    @Throws(IllegalStateException::class)
    private fun <T : Any?> getCauseOrThrow(completableFuture: CompletableFuture<T>): Executable {
        return Executable {
            try {
                val value = completableFuture.get()
                throw IllegalStateException("Unexpected $value!")
            } catch (e: ExecutionException) {
                throw e.cause!!
            }
        }
    }

    @BeforeEach
    fun beforeEach() {
        topicService = mock()
        config = ConfigFactory.empty()
            .withValue(PUBLISHER_CLIENT_ID, ConfigValueFactory.fromAnyRef("clientId123"))
        cordaPublisher = CordaPublisher(config, topicService)
    }

    @Test
    fun testPublish() {
        val futures = cordaPublisher.publish(listOf(record, record, record))
        verify(topicService, times(1)).addRecords(any())
        assertThat(futures.size).isEqualTo(3)
        for (future in futures) {
            future.get()
        }
    }

    @Test
    fun testPublishTransaction() {
        config = config.withValue(PUBLISHER_INSTANCE_ID, ConfigValueFactory.fromAnyRef("publisher-clientId123-4"))
        cordaPublisher = CordaPublisher(config, topicService)
        val futures = cordaPublisher.publish(listOf(record, record, record))
        verify(topicService, times(1)).addRecords(any())
        assertThat(futures.size).isEqualTo(1)
        for (future in futures) {
            future.get()
        }
    }

    @Test
    fun testPublishFail() {
        doThrow(CordaRuntimeException("")).whenever(topicService).addRecords(any())
        val futures = cordaPublisher.publish(listOf(record, record, record))
        verify(topicService, times(1)).addRecords(any())
        assertThat(futures.size).isEqualTo(3)
        for (future in futures) {
            assertThrows(CordaRuntimeException::class.java, getCauseOrThrow(future))
        }
    }

    @Test
    fun testPublishTransactionFail() {
        config = config.withValue(PUBLISHER_INSTANCE_ID, ConfigValueFactory.fromAnyRef("publisher-clientId123-4"))
        cordaPublisher = CordaPublisher(config, topicService)
        doThrow(CordaRuntimeException("")).whenever(topicService).addRecords(any())
        val futures = cordaPublisher.publish(listOf(record, record, record))
        verify(topicService, times(1)).addRecords(any())
        assertThat(futures.size).isEqualTo(1)
        for (future in futures) {
            assertThrows(CordaRuntimeException::class.java, getCauseOrThrow(future))
        }
    }

    @Test
    fun testClose() {
        cordaPublisher.close()
    }
}
