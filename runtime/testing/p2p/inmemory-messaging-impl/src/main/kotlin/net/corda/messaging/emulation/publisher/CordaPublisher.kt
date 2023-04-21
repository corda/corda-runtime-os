package net.corda.messaging.emulation.publisher

import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.topic.service.TopicService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

/**
 * In-memory corda publisher. Emulates CordaKafkaPublisher.
 * @property config config to store relevant information.
 * @property topicService service to interact with the in-memory storage of topics.
 */
class CordaPublisher(
    private val config: PublisherConfig,
    private val topicService: TopicService
) : Publisher {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val clientId = config.clientId
    private val transactional = config.transactional

    override fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
        return runAndCreateFutures(records.size) {
            topicService.addRecords(records)
        }
    }

    private fun runAndCreateFutures(size: Int, block: () -> Unit): List<CompletableFuture<Unit>> {
        val future = try {
            block()
            CompletableFuture.completedFuture(Unit)
        } catch (ex: Exception) {
            val message = "Corda publisher clientId $clientId, transactional $transactional, " +
                "failed to send record."
            log.error(message, ex)
            CompletableFuture.failedFuture(CordaMessageAPIFatalException(message, ex))
        }

        // if not a transaction emulate multiple sends
        return if (transactional) {
            listOf(future)
        } else {
            List(size) { future }
        }
    }

    override fun close() {
    }

    override fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {
        return runAndCreateFutures(records.size) {
            records.groupBy({ it.first }, { it.second })
                .forEach { (partitionId, recordList) ->
                    topicService.addRecordsToPartition(recordList, partitionId)
                }
        }
    }
}
