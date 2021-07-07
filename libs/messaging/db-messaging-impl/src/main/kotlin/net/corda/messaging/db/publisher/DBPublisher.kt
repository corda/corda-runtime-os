package net.corda.messaging.db.publisher

import com.google.common.util.concurrent.ThreadFactoryBuilder
import net.corda.lifecycle.LifeCycle
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.db.persistence.DBAccessProvider
import net.corda.messaging.db.persistence.DbSchema
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

class DBPublisher(private val publisherConfig: PublisherConfig,
                  private val schemaRegistry: AvroSchemaRegistry,
                  private val dbAccessProvider: DBAccessProvider,
                  private val offsetTrackersManager: OffsetTrackersManager,
                  private val threadPoolSize: Int = 5): Publisher, LifeCycle {

    companion object {
        private val log: Logger = contextLogger()
    }

    private var running = false
    private val startStopLock = ReentrantLock()

    private lateinit var executor: ExecutorService

    override val isRunning: Boolean
        get() = running

    override fun start() {
        startStopLock.withLock {
            if (!running) {
                executor = Executors.newFixedThreadPool(threadPoolSize,
                    ThreadFactoryBuilder().setNameFormat("db-publisher-thread-${publisherConfig.clientId}-%d").build())
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
     *
     * Currently, there is no support for multi-partition topics on the database implementation,
     * so all the records go to a single, fixed partition (instead of being partitioned based on their key).
     */
    override fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
        return publishToPartition(records.map { DbSchema.FIXED_PARTITION_NO to it })
    }

    override fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {
        log.trace { "Publishing records: $records" }
        val recordEntries = records.map {
            val offset = offsetTrackersManager.getNextOffset(it.second.topic)
            val serialisedKey = schemaRegistry.serialize(it.second.key).array()
            val serialisedValue = if(it.second.value != null) {
                schemaRegistry.serialize(it.second.value!!).array()
            } else {
                null
            }
            RecordDbEntry(it.second.topic, it.first, offset, serialisedKey, serialisedValue)
        }

        return if (publisherConfig.instanceId != null) {
            listOf(publishTransactionally(recordEntries))
        } else {
            publishNonTransactionally(recordEntries)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun publishTransactionally(recordEntries: List<RecordDbEntry>): CompletableFuture<Unit> {
        return CompletableFuture.supplyAsync({
            try {
                dbAccessProvider.writeRecords(recordEntries) { records ->
                    records.forEach { offsetTrackersManager.offsetReleased(it.topic, it.offset) }
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

}