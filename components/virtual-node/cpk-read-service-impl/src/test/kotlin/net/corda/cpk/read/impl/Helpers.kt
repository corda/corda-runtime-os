package net.corda.cpk.read.impl

import net.corda.crypto.core.toAvro
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId
import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer

object Helpers {

    fun dummyCpkChunkIdToChunk(
        cpkChecksum: SecureHash,
        cpkChunkPartNumber: Int,
        chunkingChecksum: SecureHash,
        bytes: ByteArray
    ) =
        CpkChunkId(cpkChecksum.toAvro(), cpkChunkPartNumber) to
                Chunk(
                    "dummyRequestId",
                    "dummyFileName",
                    chunkingChecksum.toAvro(),
                    cpkChunkPartNumber,
                    0,
                    ByteBuffer.wrap(bytes),
                    null
                )
}