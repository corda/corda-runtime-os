package net.corda.cpk.read.impl.services.cache

import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId
import java.nio.file.Path

class CpkChunksFileManagerImpl(private val commonCpkCacheDir: Path) : CpkChunksFileManager {
    init {

    }

    override fun chunkExists(chunkId: CpkChunkId): Boolean {
        TODO("Not yet implemented")
    }

    override fun writeChunk(chunkId: CpkChunkId, chunk: Chunk) {
        TODO("Not yet implemented")
    }

    override fun close() {

    }
}