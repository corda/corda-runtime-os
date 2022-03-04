package net.corda.cpk.read.impl.services.cache

import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId

interface CpkChunksFileManager : AutoCloseable {
    fun chunkExists(chunkId: CpkChunkId): Boolean

    fun writeChunk(chunkId: CpkChunkId, chunk: Chunk)
}