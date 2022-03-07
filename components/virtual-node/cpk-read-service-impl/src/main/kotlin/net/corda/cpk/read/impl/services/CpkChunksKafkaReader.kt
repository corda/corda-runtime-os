package net.corda.cpk.read.impl.services

import net.corda.chunking.toCorda
import net.corda.cpk.read.impl.services.persistence.CpkChunksFileManager
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer

// TODO should be enough for now to keep it simple and not replace/ delete CPK chunks?
class CpkChunksKafkaReader(private val cpkChunksFileManager: CpkChunksFileManager) : CompactedProcessor<CpkChunkId, Chunk> {
    // Assuming [CompactedProcessor.onSnapshot] and [CompactedProcessor.onNext] are not called concurrently.
    // This is not intended to be used as a cache, as it will not work among workers in different processes.
    // It is just used to save a disk search to check if all chunks are received.
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
        if (!cpkChunksFileManager.chunkFileExists(chunkId)) {
            cpkChunksFileManager.writeChunkFile(chunkId, chunk)
        }

        // Update cache regardless if chunk file exists because it could be that another worker wrote the chunk
        // and that could lead to cache inconsistency.
        val cpkChecksum = chunkId.cpkChecksum.toCorda()
        val chunksReceived = chunksReceivedPerCpk[cpkChecksum]
            ?: ChunksReceived().also { chunksReceivedPerCpk[cpkChecksum] = it }

        if (chunk.data.isZeroChunk()) {
            chunksReceived.expectedCount = chunkId.cpkChunkPartNumber
        } else {
            chunksReceived.chunks.add(chunkId)
        }

        if (chunksReceived.allReceived()) {
            cpkChunksFileManager.assembleCpk(cpkChecksum, chunksReceived.chunks)
        }
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