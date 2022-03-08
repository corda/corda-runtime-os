package net.corda.cpk.read.impl.services.persistence

import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId
import net.corda.v5.crypto.SecureHash
import java.nio.file.Path
import java.util.TreeSet

interface CpkChunksFileManager : AutoCloseable {
    fun chunkFileExists(chunkId: CpkChunkId): CpkChunkFileLookUp

    fun writeChunkFile(chunkId: CpkChunkId, chunk: Chunk)

    fun assembleCpk(cpkChecksum: SecureHash, chunkParts: TreeSet<CpkChunkId>): Path?
}