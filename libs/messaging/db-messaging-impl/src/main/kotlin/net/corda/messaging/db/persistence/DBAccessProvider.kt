package net.corda.messaging.db.persistence

import net.corda.lifecycle.Lifecycle
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.time.Instant

/**
 * Provides basic read/write capabilities over a relational database.
 *
 * In case of an error coming from the database, all the methods reading/writing data should rollback the corresponding transaction
 * and let the exception propagate to the client that invoked the method, so that it can be handled appropriately.
 */
interface DBAccessProvider: Lifecycle {

    /**
     * Writes the specified offsets to the database
     * @param topic the topic for which offsets will be written.
     * @param consumerGroup the consumer group for which offsets will be written.
     * @param offsetsPerPartition the offsets to commit for each partition.
     *
     * @throws OffsetsAlreadyCommittedException if any of the specified offsets have already been commited.
     *          In this case, no offsets are committed.
     */
    fun writeOffsets(topic: String, consumerGroup: String, offsetsPerPartition: Map<Int, Long>)

    /**
     * Returns the largest committed offsets for the specified partitions.
     * @param topic the topic for which offsets will be returned.
     * @param consumerGroup the consumer group for which offsets will be returned.
     * @param partitions the partitions for which offsets will be returned.
     *
     * @return a map containing the partitions as keys and the largest committed offset as values
     *          (or null, if there is no committed offset for that partition).
     */
    fun getMaxCommittedOffset(topic: String, consumerGroup: String, partitions: Set<Int>): Map<Int, Long?>

    /**
     * Returns the maximum offsets for each topic's partition from the database.
     *
     * If a partition of a topic has no records, the value null will be returned for that partition.
     *
     * @return a map containing a topic as key and a map as value, which then contains the partition as key and the offset as value.
     */
    fun getMaxOffsetsPerTopic(): Map<String, Map<Int, Long?>>

    /**
     * Writes the provided records to the database using a single transaction.
     *
     * @param postTxFn a function to be called after the transaction has been completed (either committed or rolled back).
     */
    fun writeRecords(records: List<RecordDbEntry>, postTxFn: (records: List<RecordDbEntry>, txResult: TransactionResult) -> Unit)

    /**
     * Fetch records from the specified topic within the offset windows specified.
     * @param fetchWindows the window of offsets within which records must be retrieved from the database.
     */
    fun readRecords(topic: String, fetchWindows: List<FetchWindow>): List<RecordDbEntry>

    /**
     * Retrieves a record from a topic at a specific (partition, offset) location.
     * @return the record at the specified location, or null if there is no record for this location.
     */
    fun getRecord(topic: String, partition: Int, offset: Long): RecordDbEntry?

    /**
     * Retrieve all the existing topics.
     *
     * @return a map containing the topic's name as key and the number of partitions of the topic as value.
     */
    fun getTopics(): Map<String, Int>

    /**
     * Creates a new (empty) topic in the database.
     *
     * @param topic name of the topic.
     * @param partitions the number of partitions for this topic.
     */
    fun createTopic(topic: String, partitions: Int)

    /**
     * Writes the specified offsets and records to the database atomically within a single transaction.
     *
     * @param postTxFn a function to be called after the transaction has been completed (either committed or rolled back).
     *
     * @throws OffsetsAlreadyCommittedException if any of the specified offsets have already been commited.
     *          In this case, no offsets are committed and no records are produced.
     */
    fun writeOffsetsAndRecordsAtomically(topic: String, consumerGroup: String,
                                        offsetsPerPartition: Map<Int, Long>,
                                        records: List<RecordDbEntry>,
                                        postTxFn: (records: List<RecordDbEntry>, txResult: TransactionResult) -> Unit)

    /**
     * Deletes records from the specified topic that are older than the specified timestamp.
     */
    fun deleteRecordsOlderThan(topic: String, timestamp: Instant)

    /**
     * Deletes offsets from the specified topic that are older than the specified timestamp.
     */
    fun deleteOffsetsOlderThan(topic: String, timestamp: Instant)

}

/**
 * @property partition the partition to fetch records from.
 * @param startOffset the offset after which records can be fetched (inclusive).
 * @param endOffset the offset up to which records can be fetched (inclusive).
 * @param limit the maximum number of records to be fetched.
 */
data class FetchWindow(val partition: Int, val startOffset: Long, val endOffset: Long, val limit: Int)

/**
 * Thrown when an attempt is made to commit offsets that have already been committed.
 */
class OffsetsAlreadyCommittedException: CordaRuntimeException("Offsets were already committed.")

enum class TransactionResult {
    COMMITTED,
    ROLLED_BACK
}
