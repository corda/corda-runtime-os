package net.corda.messaging.db.publisher

import com.google.common.util.concurrent.ThreadFactoryBuilder
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.db.partition.PartitionAssignor
import net.corda.messaging.db.persistence.DBAccessProvider
import net.corda.messaging.db.persistence.RecordDbEntry
import net.corda.messaging.db.sync.OffsetTrackersManager
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import org.slf4j.Logger
import java.sql.SQLClientInfoException
import java.sql.SQLNonTransientException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Suppress("LongParameterList")
class DBPublisher(private val publisherConfig: PublisherConfig,
                  private val schemaRegistry: AvroSchemaRegistry,
                  private val dbAccessProvider: DBAccessProvider,
                  private val offsetTrackersManager: OffsetTrackersManager,
                  private val partitionAssignor: PartitionAssignor,
                  private val instanceId: Int? = null,
                  private val threadPoolSize: Int = 5,
): Publisher, Lifecycle {

    companion object {
        private val log: Logger = contextLogger()
    }

    private var running = false
    private val startStopLock = ReentrantLock()

    private lateinit var executor: ExecutorService
    private lateinit var partitionsPerTopic: Map<String, Int>

    override val isRunning: Boolean
        get() = running

    override fun start() {
        startStopLock.withLock {
            if (!running) {
                executor = Executors.newFixedThreadPool(threadPoolSize,
                    ThreadFactoryBuilder().setNameFormat("db-publisher-thread-${publisherConfig.clientId}-%d").build())
                partitionsPerTopic = dbAccessProvider.getTopics()
                running = true
                log.info("Publisher started for client ID ${publisherConfig.clientId}.")
            }
        }
    }

    override fun stop() {
        startStopLock.withLock {
            if (running) {
                executor.shutdown()
                running = false
                log.info("Publisher stopped for client ID ${publisherConfig.clientId}.")
            }
        }
    }

    override fun close() {
        stop()
    }

    /**
     * Publishes the provided records.
     */
    override fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
        log.trace { "Publishing records: $records" }
        val recordEntries = records.map { record -> toDbRecord(record) }
        return publishDbRecords(recordEntries)
    }

    override fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {
        log.trace { "Publishing records: $records" }
        val recordEntries = records.map { (partition, record) ->
            toDbRecord(record, partition)
        }
        return publishDbRecords(recordEntries)
    }

    private fun publishDbRecords(recordEntries: List<RecordDbEntry>): List<CompletableFuture<Unit>> {
        return if (instanceId != null) {
            listOf(publishTransactionally(recordEntries))
        } else {
            publishNonTransactionally(recordEntries)
        }
    }

    private fun publishTransactionally(recordEntries: List<RecordDbEntry>): CompletableFuture<Unit> {
        return CompletableFuture.supplyAsync({
            try {
                dbAccessProvider.writeRecords(recordEntries) { records, _ ->
                    records.forEach { offsetTrackersManager.offsetReleased(it.topic, it.partition, it.offset) }
                }
            } catch (e: Exception) {
                val errorMessage = "Failed to publish records for client ID: ${publisherConfig.clientId}"
                log.error(errorMessage, e)
                when(e) {
                    is SQLNonTransientException, is SQLClientInfoException,  -> {
                        throw CordaMessageAPIFatalException(errorMessage, e)
                    }
                    else -> {
                        throw CordaMessageAPIIntermittentException(errorMessage, e)
                    }
                }
            }
        }, executor)
    }

    private fun publishNonTransactionally(recordEntries: List<RecordDbEntry>): List<CompletableFuture<Unit>> {
        return recordEntries.map { publishTransactionally(listOf(it)) }
    }

    private fun <K: Any, V: Any> toDbRecord(record: Record<K, V>, clientSpecifiedPartition: Int? = null): RecordDbEntry {
        val serialisedKey = schemaRegistry.serialize(record.key).array()
        val serialisedValue = if(record.value != null) {
            schemaRegistry.serialize(record.value!!).array()
        } else {
            null
        }
        val partition = clientSpecifiedPartition ?: partitionAssignor.assign(serialisedKey, partitionsPerTopic[record.topic]!!)
        val offset = offsetTrackersManager.getNextOffset(record.topic, partition)

        return RecordDbEntry(record.topic, partition, offset, serialisedKey, serialisedValue)
    }

}