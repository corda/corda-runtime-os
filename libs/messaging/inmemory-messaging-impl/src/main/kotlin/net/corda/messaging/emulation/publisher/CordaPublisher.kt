package net.corda.messaging.emulation.publisher

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture

/**
 * In-memory corda publisher. Emulates CordaKafkaPublisher.
 * @property config config to store relevant information.
 * @property topicService service to interact with the in-memory storage of topics.
 */
class CordaPublisher (
    private val config: Config,
    private val topicService: TopicService
    ) : Publisher {

    private companion object {
        private val log: Logger = contextLogger()
        const val PUBLISHER_INSTANCE_ID = "instanceId"
        const val PUBLISHER_CLIENT_ID = "clientId"
    }

    private val clientId = config.getString(PUBLISHER_CLIENT_ID)
    private val instanceId = if (config.hasPath(PUBLISHER_INSTANCE_ID)) config.getString(PUBLISHER_INSTANCE_ID) else null

    @Suppress("TooGenericExceptionCaught")
    override fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
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
    private fun getFutures(size: Int, ex: Exception? = null) : List<CompletableFuture<Unit>> {
        val futures = mutableListOf<CompletableFuture<Unit>>()
        val future = CompletableFuture<Unit>()
        futures.add(future)

        if (ex != null) {
            val message = "Corda publisher clientId $clientId, instanceId $instanceId, " +
                    "failed to send record."
            log.error(message, ex)
            future.completeExceptionally(CordaMessageAPIFatalException(message, ex))
        } else {
            future.complete(Unit)
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
        log.info("Closing Corda publisher clientId $clientId, instanceId $instanceId")
    }

    override fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {
        TODO("Not yet implemented")
    }
}
