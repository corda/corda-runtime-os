package net.corda.messaging.db.persistence

import net.corda.v5.base.annotations.VisibleForTesting
import java.lang.IllegalArgumentException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap

/**
 * This is a component used to cache records in-memory, so that recently published records can be served to subscriptions
 * from memory without querying the database.
 *
 * Records are cached in sorted form (using their offset).
 * Upon insertion of new records, the cache size for that partition is checked if it exceeds [entriesPerPartition],
 * then it is cleaned up.
 * This is done by removing entries from the beginning of the list, i.e. those with the smallest offsets.
 *
 */
class RecordsCache(topicPartitions: Map<String, Int>, private val entriesPerPartition: Int){

    private val cache: ConcurrentHashMap<String, ConcurrentHashMap<Int, ConcurrentSkipListMap<Long, RecordDbEntry>>> =
        ConcurrentHashMap()

    init {
        topicPartitions.forEach { (topic, partitions) ->
            cache[topic] = ConcurrentHashMap()
            (1..partitions).forEach { partition ->
                cache[topic]!![partition] = ConcurrentSkipListMap()
            }
        }
    }

    fun addRecords(records: List<RecordDbEntry>) {
        val cachesToCleanup = mutableSetOf<Pair<String, Int>>()

        records.forEach { record ->
            cache[record.topic]!![record.partition]!![record.offset] = record
            if (cache[record.topic]!![record.partition]!!.size > entriesPerPartition) {
                cachesToCleanup.add(record.topic to record.partition)
            }
        }

        cleanupOldRecords(cachesToCleanup)
    }

    /**
     * Returns records from cache.
     *
     * Note: this method will return a list of records only if a contiguous block of records
     * starting from [startOffset] are still in the cache.
     * In any other case (e.g. if records have been cleaned up after [startOffset], an empty list will be returned instead.
     * This is done to make this safer for clients of the caches and prevent them processing records out-of-order.
     */
    fun readRecords(topic: String, partition: Int, startOffset: Long, endOffset: Long, limit: Int): List<RecordDbEntry> {
        val cachedRecords = mutableListOf<RecordDbEntry>()
        val cache = cache[topic]!![partition]!!
        synchronized(cache) {
            val firstEntry = cache.firstEntry()
            if (firstEntry != null && firstEntry.key <= startOffset) {
                val recordsInRange = cache.subMap(startOffset, true, endOffset, true)
                var item = 1
                for (record in recordsInRange) {
                    cachedRecords.add(record.value)

                    if (item == limit) {
                        break
                    }

                    item++
                }
            }
        }
        return cachedRecords
    }

    fun addTopic(topic: String, partitions: Int) {
        if (cache.putIfAbsent(topic, ConcurrentHashMap()) == null) {
            (1..partitions).forEach { partition ->
                cache[topic]!![partition] = ConcurrentSkipListMap()
            }
        } else {
            throw IllegalArgumentException("Tried to add topic ($topic) to the cache that already existed.")
        }
    }

    @VisibleForTesting
    fun getAllEntries(topic: String, partition: Int) = cache[topic]!![partition]!!

    private fun cleanupOldRecords(cachesToCleanup: Set<Pair<String, Int>>) {
        cachesToCleanup.forEach { (topic, partition) ->
            val cache = cache[topic]!![partition]!!
            val currentSize = cache.size
            val entriesToRemove = currentSize - entriesPerPartition

            if (entriesToRemove > 0) {
                synchronized(cache) {
                    for (i in 1..entriesToRemove) {
                        cache.pollFirstEntry()
                    }
                }
            }
        }
    }

}