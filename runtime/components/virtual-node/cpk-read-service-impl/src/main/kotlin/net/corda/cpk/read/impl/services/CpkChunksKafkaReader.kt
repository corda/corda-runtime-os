package net.corda.cpk.read.impl.services

import net.corda.crypto.core.toCorda
import net.corda.cpk.read.impl.services.cache.CpkChunkIdsCache
import net.corda.cpk.read.impl.services.persistence.CpkChunksFileManager
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId
import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.CpkReader
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.utilities.VisibleForTesting
import net.corda.v5.crypto.SecureHash
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.SortedSet

// TODO should be enough for now to keep it simple and not replace/ delete CPK chunks?
class CpkChunksKafkaReader(
    private val cpkPartsDir: Path,
    private val cpkChunksFileManager: CpkChunksFileManager,
    private val onCpkAssembled: (SecureHash, Cpk) -> Unit
) : CompactedProcessor<CpkChunkId, Chunk> {
    companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    // Assuming [CompactedProcessor.onSnapshot] and [CompactedProcessor.onNext] are not called concurrently.
    // This is not intended to be used as a cache, as it will not work among workers in different processes.
    // It is just used to save extra disk searches to check if all chunks are received.
    @VisibleForTesting
    internal val receivedCpkChunksCache: CpkChunkIdsCache = CpkChunkIdsCache.Impl()

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
        chunk?.let {
            writeChunkFile(chunkId, it)
        } ?: logger.warn(
            "Ignoring null CPK chunk: ${chunkId.cpkChecksum} : ${chunkId.cpkChunkPartNumber}"
        )
    }

    @VisibleForTesting
    internal fun writeChunkFile(chunkId: CpkChunkId, chunk: Chunk) {
        val cpkChunkFileLookUp = cpkChunksFileManager.chunkFileExists(chunkId)
        if (!cpkChunkFileLookUp.exists) {
            cpkChunksFileManager.writeChunkFile(chunkId, chunk)
        } else {
            logger.info(
                "Skipped writing CPK chunk to disk cache as it already exists: " +
                        "${cpkChunkFileLookUp.path}"
            )
        }

        // We need to update CPK chunks received cache even if a CPK chunk file already exists on disk so that the worker
        // can have a correct picture of received CPK chunks. A CPK chunk file could already exist in a scenario where
        // for e.g. multiple DB workers would share the same filesystem.
        val cpkChecksum = chunkId.cpkChecksum.toCorda()
        receivedCpkChunksCache.addOrSetExpected(cpkChecksum, chunkId, chunk.isZeroChunk())

        if (receivedCpkChunksCache.allChunksReceived(cpkChecksum)) {
            val chunkIds = receivedCpkChunksCache.getChunkIds(cpkChecksum)!!
            onAllChunksReceived(cpkChecksum, chunkIds)
        }
    }

    private fun onAllChunksReceived(cpkChecksum: SecureHash, chunks: SortedSet<CpkChunkId>) {
        val cpkPath = cpkChunksFileManager.assembleCpk(cpkChecksum, chunks)
        cpkPath?.let {
            val cpk = Files.newInputStream(it).use { inStream ->
                CpkReader.readCpk(inStream, cpkPartsDir)
            }
            onCpkAssembled(cpk.metadata.fileChecksum, cpk)
        } ?: logger.warn("CPK assemble has failed for: $cpkChecksum")
        receivedCpkChunksCache.remove(cpkChecksum)
    }
}

private fun Chunk.isZeroChunk() = this.data.limit() == 0
