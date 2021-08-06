package net.corda.messaging.db.persistence

import net.corda.v5.base.annotations.VisibleForTesting
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A [DBAccessProvider] that acts as a cache proxy on top of a [DBAccessProviderImpl].
 * It caches written records using a [RecordsCache] and reads records from the database only if they can't be served from the cache.
 *
 * An important thing to note here is that due to the nature of publication, records might be added out-of-order on the cache.
 * However, offset tracking will ensure [readRecords] is called with windows that have end offsets matching the max visible offsets.
 * This means lists of records returned from the cache are guaranteed to contain contiguous records without any gaps.
 * Furthermore, to keep things simple and avoid causing more load to the database, we do not attempt
 * to combine records from cache with records from the database for a single partition.
 * For each partition's fetch window, we either serve as many records as we can from the cache
 * or alternatively read as many records as we can from the DB.
 */
class DBAccessProviderCached(private val dbAccessProviderImpl: DBAccessProviderImpl, private val cachedRecordsPerPartition: Int):
    DBAccessProvider by dbAccessProviderImpl {

    private lateinit var recordsCache: RecordsCache

    private var running = false
    private val startStopLock = ReentrantLock()

    override val isRunning: Boolean
        get() = running

    override fun start() {
        startStopLock.withLock {
            if (!running) {
                dbAccessProviderImpl.start()
                val topics = dbAccessProviderImpl.getTopics()
                recordsCache = RecordsCache(topics, cachedRecordsPerPartition)
                running = true
            }
        }
    }

    override fun stop() {
        startStopLock.withLock {
            if (running) {
                dbAccessProviderImpl.stop()
                running = false
            }
        }
    }

    @VisibleForTesting
    fun getCache() = recordsCache

    override fun readRecords(topic: String, fetchWindows: List<FetchWindow>): List<RecordDbEntry> {
        val records = mutableListOf<RecordDbEntry>()

        val windowsForDb = fetchWindows.filter {
            val recordsFromCache = recordsCache.readRecords(topic, it.partition, it.startOffset, it.endOffset, it.limit)

            if (recordsFromCache.isNotEmpty()) {
                records.addAll(recordsFromCache)
            }

            recordsFromCache.isEmpty()
        }

        val recordsFromDb = dbAccessProviderImpl.readRecords(topic, windowsForDb)
        records.addAll(recordsFromDb)

        return records
    }

    override fun writeRecords(records: List<RecordDbEntry>, postTxFn: (records: List<RecordDbEntry>, txResult: TransactionResult) -> Unit) {
        dbAccessProviderImpl.writeRecords(records) { writtenRecords, txResult ->
            if (txResult == TransactionResult.COMMITTED) {
                recordsCache.addRecords(records)
            }
            postTxFn(writtenRecords, txResult)
        }
    }

    override fun writeOffsetsAndRecordsAtomically(
        topic: String,
        consumerGroup: String,
        offsetsPerPartition: Map<Int, Long>,
        records: List<RecordDbEntry>,
        postTxFn: (records: List<RecordDbEntry>, txResult: TransactionResult) -> Unit
    ) {
        dbAccessProviderImpl.writeOffsetsAndRecordsAtomically(topic, consumerGroup, offsetsPerPartition, records)
        { writtenRecords, txResult ->
            if (txResult == TransactionResult.COMMITTED) {
                recordsCache.addRecords(records)
            }
            postTxFn(writtenRecords, txResult)
        }
    }

    override fun createTopic(topic: String, partitions: Int) {
        dbAccessProviderImpl.createTopic(topic, partitions)
        recordsCache.addTopic(topic, partitions)
    }

}