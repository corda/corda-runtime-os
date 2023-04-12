package net.corda.rocks.db.api

import net.corda.lifecycle.Resource

interface StorageManager : Resource {
    fun start()
    fun createTableIfNotExists(table: String)
    fun get(table: String, key: ByteArray): ByteArray?

    fun estimateKeys(table: String): Int
    fun put(table: String, key: ByteArray, value: ByteArray)
    fun delete(table: String, key: ByteArray)
    fun deleteRange(table: String, start: ByteArray, endExclusive: ByteArray)
    fun batchWrite(ops: List<WriteOp>)
    fun batchRead(ops: List<ReadOp>): List<ReadResult>
    fun iterate(table: String, start: ByteArray, endExclusive: ByteArray, processor: QueryProcessor)
    fun iterateAll(table: String, processor: QueryProcessor)
    fun flush(table: String)
}