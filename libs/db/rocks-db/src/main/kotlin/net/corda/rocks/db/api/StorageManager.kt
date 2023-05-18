package net.corda.rocks.db.api

import net.corda.lifecycle.Resource

/**
 * Interface used to interact with the rocks db storage engine
 */
interface StorageManager : Resource {

    /**
     * Start the storage manager
     */
    fun start()

    /**
     * Create the given [table] if it does not already exist
     */
    fun createTableIfNotExists(table: String)

    /**
     * Get the value in bytes for the given [key] from table [table]
     */
    fun get(table: String, key: ByteArray): ByteArray?

    /**
     * Estimate the number of keys stored in a [table]
     */
    fun estimateKeys(table: String): Int

    /**
     * Put the given [value] in a [table] with the given [key]
     */
    fun put(table: String, key: ByteArray, value: ByteArray)

    /**
     * Delete the value in a [table] for the given [key]
     */
    fun delete(table: String, key: ByteArray)

    /**
     * Delete the values from a [table] for the keys in the range of [start] to [endExclusive]
     */
    fun deleteRange(table: String, start: ByteArray, endExclusive: ByteArray)

    /**
     * Batch write of [ops]
     */
    fun batchWrite(ops: List<WriteOp>)

    /**
     * Batch of read [ops]
     */
    fun batchRead(ops: List<ReadOp>): List<ReadResult>

    /**
     * Iterate through a [table], operating on each key/value pair using the [processor],
     * for all values in the given range of keys [start] and [endExclusive]
     */
    fun iterate(table: String, start: ByteArray, endExclusive: ByteArray, processor: QueryProcessor)

    /**
     * Iterate through a [table], operating on each key/value pair using the [processor]
     */
    fun iterateAll(table: String, processor: QueryProcessor)

    /**
     * Flush the data from the given [table]
     */
    fun flush(table: String)
}
