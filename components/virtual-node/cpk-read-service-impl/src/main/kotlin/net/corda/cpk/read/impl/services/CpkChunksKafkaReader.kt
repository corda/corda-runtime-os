package net.corda.cpk.read.impl.services

import net.corda.chunking.toCorda
import net.corda.cpk.read.impl.services.persistence.CpkChunksFileManager
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId
import net.corda.libs.packaging.CpkIdentifier
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.packaging.CPK
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.TreeSet

// TODO should be enough for now to keep it simple and not replace/ delete CPK chunks?
class CpkChunksKafkaReader(
    private val tempCpkCacheDir: Path,
    private val cpkChunksFileManager: CpkChunksFileManager,
    private val onCpkAssembled: (CpkIdentifier, CPK) -> Unit
) : CompactedProcessor<CpkChunkId, Chunk> {
    companion object {
        val logger = contextLogger()
    }

    // Assuming [CompactedProcessor.onSnapshot] and [CompactedProcessor.onNext] are not called concurrently.
    // This is not intended to be used as a cache, as it will not work among workers in different processes.
    // It is just used to save extra disk searches to check if all chunks are received.
    private val chunksReceivedPerCpk = mutableMapOf<SecureHash, ChunksReceived>()

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
        val cpkChunkFileLookUp = cpkChunksFileManager.chunkFileExists(chunkId)
        if (!cpkChunkFileLookUp.exists) {
            cpkChunksFileManager.writeChunkFile(chunkId, chunk)
        } else {
            logger.info(
                "Skipped writing CPK chunk to disk as it already exists: " +
                        "${cpkChunkFileLookUp.path}"
            )
        }

        // We need to update CPK chunks received cache even if a CPK chunk file already exists on disk so that the worker
        // can have a correct picture of received CPK chunks. A CPK chunk file could already exist in a scenario where
        // for e.g. multiple DB workers would share the same filesystem.
        val cpkChecksum = chunkId.cpkChecksum.toCorda()
        val chunksReceived = chunksReceivedPerCpk[cpkChecksum]
            ?: ChunksReceived().also { chunksReceivedPerCpk[cpkChecksum] = it }

        if (chunk.data.isZeroChunk()) {
            chunksReceived.expectedCount = chunkId.cpkChunkPartNumber
        } else {
            chunksReceived.chunks.add(chunkId)
        }

        if (chunksReceived.allReceived()) {
            onAllChunksReceived(cpkChecksum, chunksReceived.chunks)
        }
    }

    private fun onAllChunksReceived(cpkChecksum: SecureHash, chunks: TreeSet<CpkChunkId>) {
        val cpkPath = cpkChunksFileManager.assembleCpk(cpkChecksum, chunks)
        cpkPath?.let {
            val cpk = Files.newInputStream(it).use { inStream ->
                CPK.from(inStream, tempCpkCacheDir)
            }
            onCpkAssembled(CpkIdentifier.fromLegacy(cpk.metadata.id), cpk)
        } ?: logger.warn("CPK assemble has failed for: $cpkChecksum")
        chunksReceivedPerCpk.remove(cpkChecksum)
    }

    private class ChunksReceived {
        val chunks = sortedSetOf<CpkChunkId>()
        var expectedCount = -1
            set(value) {
                if (field == -1) {
                    field = value
                }
            }

        fun allReceived() =
            chunks.size == expectedCount
    }
}

private fun ByteBuffer.isZeroChunk() = this.limit() == 0
