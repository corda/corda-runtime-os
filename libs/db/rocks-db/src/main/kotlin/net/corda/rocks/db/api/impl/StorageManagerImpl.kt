package net.corda.rocks.db.api.impl

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import net.corda.libs.configuration.SmartConfig
import net.corda.rocks.db.api.KeyValueOpType
import net.corda.rocks.db.api.QueryProcessor
import net.corda.rocks.db.api.ReadOp
import net.corda.rocks.db.api.ReadResult
import net.corda.rocks.db.api.StorageManager
import net.corda.rocks.db.api.WriteOp
import net.corda.schema.configuration.ConfigKeys.WORKSPACE_DIR
import org.rocksdb.ColumnFamilyDescriptor
import org.rocksdb.ColumnFamilyHandle
import org.rocksdb.ColumnFamilyOptions
import org.rocksdb.DBOptions
import org.rocksdb.FlushOptions
import org.rocksdb.ReadOptions
import org.rocksdb.RocksDB
import org.rocksdb.Slice
import org.rocksdb.WriteBatch
import org.rocksdb.WriteOptions

class StorageManagerImpl(config: SmartConfig) : StorageManager {
    private val dbFolder: Path = Path.of(config.getString(WORKSPACE_DIR))
    private val columnFamilyHandles = ConcurrentHashMap<String, ColumnFamilyHandle>()
    private var rocksDB: RocksDB? = null
    private var dbOptions: DBOptions? = null
    private var columnOptions: ColumnFamilyOptions? = null

    override fun estimateKeys(table: String): Int {
        return rocksDB?.getProperty(getColumnHandle(table), "rocksdb.estimate-num-keys" )?.toInt() ?: 0
    }
    override fun start() {
        if (rocksDB == null) {
            val openResult = RocksDBHelper.initRocksDB(dbFolder.toAbsolutePath())
            rocksDB = openResult.rocksDB
            dbOptions = openResult.dbOptions
            columnOptions = openResult.columnOptions
            columnFamilyHandles.putAll(openResult.columnFamilyHandles)
        }
    }

    override fun createTableIfNotExists(table: String) {
        require(rocksDB != null) {
            "No DB started"
        }
        columnFamilyHandles.computeIfAbsent(table) { key ->
            rocksDB!!.createColumnFamily(ColumnFamilyDescriptor(key.toByteArray(Charsets.UTF_8), columnOptions!!))
        }
    }

    private fun getColumnHandle(table: String): ColumnFamilyHandle {
        return columnFamilyHandles[table] ?: throw IllegalArgumentException("No table group handle")
    }

    override fun get(table: String, key: ByteArray): ByteArray? {
        require(rocksDB != null) {
            "No DB started"
        }
        val columnHandle = getColumnHandle(table)
        return rocksDB!!.get(columnHandle, key)
    }

    override fun put(table: String, key: ByteArray, value: ByteArray) {
        require(rocksDB != null) {
            "No DB started"
        }
        val columnHandle = getColumnHandle(table)
        rocksDB!!.put(columnHandle, key, value)
    }

    override fun delete(table: String, key: ByteArray) {
        require(rocksDB != null) {
            "No DB started"
        }
        val columnHandle = getColumnHandle(table)
        rocksDB!!.delete(columnHandle, key)
    }

    override fun deleteRange(table: String, start: ByteArray, endExclusive: ByteArray) {
        require(rocksDB != null) {
            "No DB started"
        }
        val columnHandle = getColumnHandle(table)
        rocksDB!!.deleteRange(columnHandle, start, endExclusive)
    }

    override fun batchWrite(ops: List<WriteOp>) {
        require(rocksDB != null) {
            "No DB started"
        }
        if (ops.isEmpty()) {
            return
        }
        WriteBatch().use { batch ->
            for (op in ops) {
                val columnHandle = getColumnHandle(op.table)
                when (op.type) {
                    KeyValueOpType.Put -> {
                        batch.put(columnHandle, op.key, op.value!!)
                    }
                    KeyValueOpType.Delete -> {
                        batch.delete(columnHandle, op.key)
                    }
                }
            }
            rocksDB!!.write(WriteOptions(), batch)
        }
    }

    override fun batchRead(ops: List<ReadOp>): List<ReadResult> {
        require(rocksDB != null) {
            "No DB started"
        }
        if (ops.isEmpty()) {
            return emptyList()
        }
        val snapshot = rocksDB!!.snapshot
        try {
            val columnHandleList = ops.map { getColumnHandle(it.table) }
            val keyList = ops.map { it.key }
            val readOptions = ReadOptions().setSnapshot(snapshot)
            val values = rocksDB!!.multiGetAsList(
                readOptions,
                columnHandleList,
                keyList
            )

            return ops.mapIndexed { index, readOp ->
                ReadResult(readOp.table, readOp.key, values[index])
            }
        } finally {
            rocksDB!!.releaseSnapshot(snapshot)
            snapshot.close()
        }
    }

    override fun iterate(table: String, start: ByteArray, endExclusive: ByteArray, processor: QueryProcessor) {
        require(rocksDB != null) {
            "No DB started"
        }
        val columnHandle = getColumnHandle(table)
        val snapshot = rocksDB!!.snapshot
        try {
            val readOptions = ReadOptions().setIterateUpperBound(Slice(endExclusive)).setSnapshot(snapshot)
            readOptions.use {
                rocksDB!!.newIterator(columnHandle, readOptions).use { iterator ->
                    iterator.seek(start)
                    while (iterator.isValid) {
                        if (!processor.process(iterator.key(), iterator.value())) {
                            break
                        }
                        iterator.next()
                    }
                }
            }
        } finally {
            rocksDB!!.releaseSnapshot(snapshot)
            snapshot.close()
        }
    }

    override fun iterateAll(table: String, processor: QueryProcessor) {
        require(rocksDB != null) {
            "No DB started"
        }
        val columnHandle = getColumnHandle(table)
        val snapshot = rocksDB!!.snapshot
        try {
            val readOptions = ReadOptions().setSnapshot(snapshot)
            readOptions.use {
                rocksDB!!.newIterator(columnHandle, readOptions).use { iterator ->
                    iterator.seekToFirst()
                    while (iterator.isValid) {
                        if (!processor.process(iterator.key(), iterator.value())) {
                            break
                        }
                        iterator.next()
                    }
                }
            }
        } finally {
            rocksDB!!.releaseSnapshot(snapshot)
            snapshot.close()
        }
    }

    override fun flush(table: String) {
        require(rocksDB != null) {
            "No DB started"
        }
        val columnHandle = getColumnHandle(table)
        val flushOptions = FlushOptions().setWaitForFlush(true)
        rocksDB!!.flush(flushOptions, columnHandle)
        flushOptions.close()
    }

    override fun close() {
        for (handle in columnFamilyHandles.values) {
            handle.close()
        }
        columnFamilyHandles.clear()
        rocksDB?.close()
        rocksDB = null
        columnOptions?.close()
        columnOptions = null
        dbOptions?.close()
        dbOptions = null
    }

}