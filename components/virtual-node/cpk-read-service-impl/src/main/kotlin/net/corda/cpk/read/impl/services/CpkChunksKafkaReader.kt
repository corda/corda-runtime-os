package net.corda.cpk.read.impl.services

import net.corda.cpk.read.impl.services.persistence.CpkChunksFileManager
import net.corda.cpk.read.impl.services.persistence.CpkChunksFileManagerImpl
import net.corda.cpk.readwrite.CpkServiceConfigKeys
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.nio.file.Paths

// TODO should be enough for now to keep it simple and not replace/ delete CPK chunks?
class CpkChunksKafkaReader(config: SmartConfig) : CompactedProcessor<CpkChunkId, Chunk> {

    private val cpkChunksFileManager: CpkChunksFileManager

    init {
        val commonCpkCacheDir = config.let {
            it.getString(CpkServiceConfigKeys.CPK_CACHE_DIR)?.let { cpkCacheDirConfig ->
                Paths.get(cpkCacheDirConfig)
            } ?: throw CordaRuntimeException("CPK cache directory configuration not found")
        }
        cpkChunksFileManager = CpkChunksFileManagerImpl(commonCpkCacheDir)
    }

    override val keyClass: Class<CpkChunkId>
        get() = CpkChunkId::class.java
    override val valueClass: Class<Chunk>
        get() = Chunk::class.java

    override fun onSnapshot(currentData: Map<CpkChunkId, Chunk>) {
        currentData.forEach {
            val chunkId = it.key
            val chunk = it.value
            writeChunkFile(chunkId, chunk)
        }
    }

    override fun onNext(newRecord: Record<CpkChunkId, Chunk>, oldValue: Chunk?, currentData: Map<CpkChunkId, Chunk>) {
        val chunkId = newRecord.key
        val chunk = newRecord.value
        writeChunkFile(chunkId, chunk!!) // assuming not nullable for now
    }

    private fun writeChunkFile(chunkId: CpkChunkId, chunk: Chunk) {
        if (!cpkChunksFileManager.chunkFileExists(chunkId)) {
            cpkChunksFileManager.writeChunkFile(chunkId, chunk)
        }
    }
}