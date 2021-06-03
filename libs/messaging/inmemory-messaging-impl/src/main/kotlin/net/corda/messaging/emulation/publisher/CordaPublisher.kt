package net.corda.messaging.emulation.publisher

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory.Companion.PUBLISHER_CLIENT_ID
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory.Companion.PUBLISHER_INSTANCE_ID
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory.Companion.PUBLISHER_TOPIC
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.v5.base.concurrent.CordaFuture
import net.corda.v5.base.internal.concurrent.OpenFuture
import net.corda.v5.base.internal.concurrent.openFuture
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * In-memory corda publisher. Emulates CordaKafkaPublisher.
 * @property config config to store relevant information.
 * @property topicService service to interact with the in-memory storage of topics.
 */
class CordaPublisher<K : Any, V : Any> (
    private val config: Config,
    private val topicService: TopicService
    ) : Publisher<K, V> {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private val topic = config.getString(PUBLISHER_TOPIC)
    private val clientId = config.getString(PUBLISHER_CLIENT_ID)
    private val instanceId = if (config.hasPath(PUBLISHER_INSTANCE_ID)) config.getInt(PUBLISHER_INSTANCE_ID) else null

    @Suppress("TooGenericExceptionCaught")
    override fun publish(records: List<Record<K, V>>): List<CordaFuture<Unit>> {
        return try {
            topicService.addRecords(records)
            getFutures(records.size)
        } catch (ex: Exception) {
            getFutures(records.size, ex)
        }
    }

    /**
     * Generate result for publish to topics.
     * In-memory publish is always done as a transaction.
     * [Publisher] api expects each record to be sent separately if transaction is not enabled.
     * Emulate multiple sends by copying transaction result to a list of futures [size] times.
     */
    private fun getFutures(size: Int, ex: Exception? = null) : List<OpenFuture<Unit>> {
        val futures = mutableListOf<OpenFuture<Unit>>()
        val future = openFuture<Unit>()
        futures.add(future)

        if (ex != null) {
            val message = "Corda publisher clientId $clientId, instanceId $instanceId, " +
                    "for topic $topic failed to send record."
            log.error(message, ex)
            future.setException(CordaMessageAPIFatalException(message, ex))
        } else {
            future.set(Unit)
        }

        //if not a transaction emulate multiple sends
        if (instanceId == null) {
            repeat(size - 1) {
                futures.add(future)
            }
        }

        return futures
    }

    override fun close() {
        log.info("Closing Corda publisher clientId $clientId, instanceId $instanceId, " +
                "for topic $topic.")
    }

    override fun publishToPartition(records: List<Pair<Int, Record<K, V>>>): List<CordaFuture<Unit>> {
        TODO("Not yet implemented")
    }
}
