package net.corda.cpk.read.impl

import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId
import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer
import net.corda.crypto.core.avro.toAvro

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
                    chunkingChecksum.toAvro(),
                    cpkChunkPartNumber,
                    0,
                    ByteBuffer.wrap(bytes),
                    null
                )
}
