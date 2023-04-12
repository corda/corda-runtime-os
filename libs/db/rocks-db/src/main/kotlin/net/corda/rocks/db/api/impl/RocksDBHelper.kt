package net.corda.rocks.db.api.impl

import java.nio.file.Files
import java.nio.file.Path
import org.rocksdb.BlockBasedTableConfig
import org.rocksdb.BloomFilter
import org.rocksdb.ColumnFamilyDescriptor
import org.rocksdb.ColumnFamilyHandle
import org.rocksdb.ColumnFamilyOptions
import org.rocksdb.CompactionStyle
import org.rocksdb.CompressionType
import org.rocksdb.DBOptions
import org.rocksdb.Env
import org.rocksdb.InfoLogLevel
import org.rocksdb.LRUCache
import org.rocksdb.Options
import org.rocksdb.RocksDB
import org.rocksdb.SstFileManager
import org.rocksdb.Statistics

object RocksDBHelper {
    init {
        RocksDB.loadLibrary()
    }

    data class DBOpenResult(
        val rocksDB: RocksDB,
        val dbOptions: DBOptions,
        val columnOptions: ColumnFamilyOptions,
        val columnFamilyHandles: Map<String, ColumnFamilyHandle>
    )

    fun createDbOptions(): DBOptions {
        val sstFileManager = SstFileManager(Env.getDefault())
        val statistics = Statistics()
        return DBOptions()
            .setCreateIfMissing(true)
            .setCreateMissingColumnFamilies(true)
            .setIncreaseParallelism(2)
            .setInfoLogLevel(InfoLogLevel.INFO_LEVEL)
            .setMaxTotalWalSize(0L)
            .setStatistics(statistics)
            .setSstFileManager(sstFileManager)
            .setWalSizeLimitMB(0L)
            .setMaxSubcompactions(1)
    }

    fun createOptions(dbOptions: DBOptions, cfOptions: ColumnFamilyOptions): Options {
        val sstFileManager = SstFileManager(Env.getDefault())
        val statistics = Statistics()
        return Options(dbOptions, cfOptions)
            .optimizeLevelStyleCompaction()
            .setCompactionReadaheadSize(0)
            .setCompactionStyle(CompactionStyle.LEVEL)
            .setCreateIfMissing(false)
            .setCreateMissingColumnFamilies(true)
            .setIncreaseParallelism(2)
            .setInfoLogLevel(InfoLogLevel.INFO_LEVEL)
            .setMaxWriteBufferNumber(3)
            .setNumLevels(4)
            .setSstFileManager(sstFileManager)
            .setStatistics(statistics)
            .setTargetFileSizeMultiplier(2)
            .setWalSizeLimitMB(0L)
            .setWalTtlSeconds(0L)
            .setMaxSubcompactions(1)
    }

    fun createColumnFamilyOptions(): ColumnFamilyOptions {
        val bloomFilter = BloomFilter(10.0)
        val tableConfig = BlockBasedTableConfig()
            .setBlockCache(LRUCache(64 * 1024, 6))
            .setFilterPolicy(bloomFilter)
            .setBlockSizeDeviation(5)
            .setBlockRestartInterval(10)
            .setCacheIndexAndFilterBlocks(true)
            .setBlockCacheCompressed(LRUCache(64 * 1000, 10))
        return ColumnFamilyOptions()
            .setCompactionStyle(CompactionStyle.LEVEL)
            .setMaxWriteBufferNumber(2)
            .setNumLevels(4)
            .setTargetFileSizeMultiplier(2)
            .setCompressionType(CompressionType.SNAPPY_COMPRESSION)
            .setTableFormatConfig(tableConfig)
    }

    fun initRocksDB(dbPath: Path): DBOpenResult {
        Files.createDirectories(dbPath)

        val dbOptions = createDbOptions()
        val columnOptions = createColumnFamilyOptions()
        val generalOptions = createOptions(dbOptions, columnOptions)
        val columnFamilies = RocksDB.listColumnFamilies(generalOptions, dbPath.toString())
        val columnFamilyDescriptors = ArrayList<ColumnFamilyDescriptor>(columnFamilies.size + 1)
        val columnFamilyHandles = ArrayList<ColumnFamilyHandle>(columnFamilies.size + 1)

        if (columnFamilies != null && columnFamilies.isNotEmpty()) {
            for (columnFamily in columnFamilies) {
                columnFamilyDescriptors += ColumnFamilyDescriptor(columnFamily, columnOptions)
            }
        } else {
            columnFamilyDescriptors += ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, columnOptions)
        }

        val rocksDb = RocksDB.open(
            dbOptions,
            dbPath.toString(),
            columnFamilyDescriptors,
            columnFamilyHandles
        )
        val handleMap = columnFamilyHandles.mapIndexed { index, columnFamilyHandle ->
            Pair(if (index < columnFamilies.size) String(columnFamilies[index]) else "default", columnFamilyHandle)
        }.toMap()
        return DBOpenResult(rocksDb, dbOptions, columnOptions, handleMap)
    }
}