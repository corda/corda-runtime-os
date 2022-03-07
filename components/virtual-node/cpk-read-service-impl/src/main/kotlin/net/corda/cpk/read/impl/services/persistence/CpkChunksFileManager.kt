package net.corda.cpk.read.impl.services.persistence

import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId

interface CpkChunksFileManager : AutoCloseable {
    fun chunkFileExists(chunkId: CpkChunkId): Boolean

    fun writeChunkFile(chunkId: CpkChunkId, chunk: Chunk)
}