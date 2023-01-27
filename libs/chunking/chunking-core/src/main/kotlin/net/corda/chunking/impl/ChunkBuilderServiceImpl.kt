package net.corda.chunking.impl

import java.nio.ByteBuffer
import net.corda.chunking.ChunkBuilderService
import net.corda.chunking.toAvro
import net.corda.data.KeyValuePairList
import net.corda.data.chunking.Chunk
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Component

@Component(service = [ChunkBuilderService::class])
class ChunkBuilderServiceImpl : ChunkBuilderService {
    override fun buildFinalChunk(
        identifier: String,
        chunkNumber: Int,
        checksum: SecureHash,
        offset: Long?,
        properties: KeyValuePairList?,
        fileName: String?,
    ): Chunk = Chunk.newBuilder()
        .setRequestId(identifier)
        .setPartNumber(chunkNumber)
        .setData(ByteBuffer.wrap(ByteArray(0)))
        .setChecksum(checksum.toAvro())
        .setProperties(properties)
        .setOffset(offset)
        //TODO - remove these CORE-9481
        .setFileName(fileName)
        .build()

    override fun buildChunk(
        identifier: String,
        chunkNumber: Int,
        byteBuffer: ByteBuffer,
        offset: Long?,
        properties: KeyValuePairList?,
        fileName: String?,
    ): Chunk = Chunk.newBuilder()
        .setRequestId(identifier)
        .setPartNumber(chunkNumber)
        .setData(byteBuffer)
        .setProperties(properties)
        .setChecksum(null)
        .setOffset(offset)
        //TODO - remove these CORE-9481
        .setFileName(fileName)
        .build()
}