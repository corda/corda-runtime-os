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
        properties: KeyValuePairList?,
        fileName: String?,
        offset: Long?,
    ): Chunk = Chunk.newBuilder()
        .setRequestId(identifier)
        .setPartNumber(chunkNumber)
        .setData(ByteBuffer.wrap(ByteArray(0)))
        .setChecksum(checksum.toAvro())
        .setProperties(properties)
        //TODO - remove these CORE-9481
        .setFileName(fileName)
        .setOffset(offset)
        .build()


    override fun buildChunk(
        identifier: String,
        chunkNumber: Int,
        byteBuffer: ByteBuffer,
        properties: KeyValuePairList?,
        fileName: String?,
        offset: Long?,
    ): Chunk = Chunk.newBuilder()
        .setRequestId(identifier)
        .setPartNumber(chunkNumber)
        .setData(byteBuffer)
        .setProperties(properties)
        //TODO - remove these CORE-9481
        .setFileName(fileName)
        .setChecksum(null)
        .setOffset(offset)
        .build()

}