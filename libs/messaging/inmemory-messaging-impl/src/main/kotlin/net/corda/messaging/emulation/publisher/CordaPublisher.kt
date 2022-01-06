package net.corda.messaging.emulation.publisher

import com.typesafe.config.Config
import net.corda.libs.configuration.schema.messaging.INSTANCE_ID
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
class CordaPublisher(
    private val config: Config,
    private val topicService: TopicService
) : Publisher {

    private companion object {
        private val log: Logger = contextLogger()
        const val PUBLISHER_CLIENT_ID = "clientId"
    }

    private val clientId = config.getString(PUBLISHER_CLIENT_ID)
    private val instanceId = if (config.hasPath(INSTANCE_ID)) config.getString(INSTANCE_ID) else null

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
            val message = "Corda publisher clientId $clientId, instanceId $instanceId, " +
                "failed to send record."
            log.error(message, ex)
            CompletableFuture.failedFuture(CordaMessageAPIFatalException(message, ex))
        }

        // if not a transaction emulate multiple sends
        return if (instanceId != null) {
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
