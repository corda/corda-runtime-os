package net.corda.messaging.db.persistence

import net.corda.lifecycle.LifeCycle

/**
 * Provides basic read/write capabilities over a relational database.
 *
 * In case of an error coming from the database, all the methods reading/writing data should rollback the corresponding transaction
 * and let the exception propagate to the client that invoked the method, so that it can be handled appropriately.
 */
interface DBAccessProvider: LifeCycle {

    /**
     * Writes the specified offset for the specified topic and consumer group to the database.
     */
    fun writeOffset(topic: String, consumerGroup: String, offset: Long)

    /**
     * Returns the largest committed offset for the specified consumer group in the specified topic,
     * or null if there is no committed offset for that topic.
     */
    fun getMaxCommittedOffset(topic: String, consumerGroup: String): Long?

    /**
     * Returns the maximum offset for each topic from the database.
     *
     * If a topic has no records, the value null will be returned for that topic.
     */
    fun getMaxOffsetPerTopic(): Map<String, Long?>

    /**
     * Writes the provided records to the database using a single transaction.
     *
     * @param postTxFn a function to be called after the transaction has been completed (either committed or rolled back).
     */
    fun writeRecords(records: List<RecordDbEntry>, postTxFn: (records: List<RecordDbEntry>) -> Unit)

    /**
     * Fetch records from the specified topic within the window offsets specified.
     * @param startOffset the starting offset to fetch records from (inclusive).
     * @param maxOffset the max offset up to which to fetch records (inclusive).
     * @param maxNumberOfRecords the maximum number of records to fetch from the database.
     */
    fun readRecords(topic: String, startOffset: Long, maxOffset: Long, maxNumberOfRecords: Int): List<RecordDbEntry>

    /**
     * Retrieves a record from a topic at a specific (partition, offset) location.
     * @return the record at the specified location, or null if there is no record for this location.
     */
    fun getRecord(topic: String, partition: Int, offset: Long): RecordDbEntry?

    /**
     * Creates a new (empty) topic in the database.
     *
     * @param the name of the topic.
     */
    fun createTopic(topic: String)

    /**
     * Writes the specified offset and records to the database atomically within a single transaction.
     *
     * @param postTxFn a function to be called after the transaction has been completed (either committed or rolled back).
     */
    fun writeOffsetAndRecordsAtomically(topic: String, consumerGroup: String,
                                        offset: Long, records: List<RecordDbEntry>,
                                        postTxFn: (records: List<RecordDbEntry>) -> Unit)

}

data class RecordDbEntry(val topic: String, val partition: Int, val offset: Long, val key: ByteArray, val value: ByteArray?) {
    @Suppress("ComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RecordDbEntry

        if (topic != other.topic) return false
        if (partition != other.partition) return false
        if (offset != other.offset) return false
        if (!key.contentEquals(other.key)) return false
        if (value != null) {
            if (other.value == null) return false
            if (!value.contentEquals(other.value)) return false
        } else if (other.value != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = topic.hashCode()
        result = 31 * result + partition
        result = 31 * result + offset.hashCode()
        result = 31 * result + key.contentHashCode()
        result = 31 * result + (value?.contentHashCode() ?: 0)
        return result
    }
}