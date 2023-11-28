package net.corda.chunking.impl

import net.corda.chunking.ChunkBuilderService
import net.corda.crypto.core.toAvro
import net.corda.data.KeyValuePairList
import net.corda.data.chunking.Chunk
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer

@Component(service = [ChunkBuilderService::class])
class ChunkBuilderServiceImpl : ChunkBuilderService {
    override fun buildFinalChunk(
        identifier: String,
        chunkNumber: Int,
        checksum: SecureHash,
        offset: Long,
        properties: KeyValuePairList?
    ): Chunk = Chunk.newBuilder()
        .setRequestId(identifier)
        .setPartNumber(chunkNumber)
        .setData(ByteBuffer.wrap(ByteArray(0)))
        .setChecksum(checksum.toAvro())
        .setProperties(properties)
        .setOffset(offset)
        .build()

    override fun buildChunk(
        identifier: String,
        chunkNumber: Int,
        byteBuffer: ByteBuffer,
        offset: Long,
        properties: KeyValuePairList?
    ): Chunk = Chunk.newBuilder()
        .setRequestId(identifier)
        .setPartNumber(chunkNumber)
        .setData(byteBuffer)
        .setProperties(properties)
        .setChecksum(null)
        .setOffset(offset)
        .build()
}
