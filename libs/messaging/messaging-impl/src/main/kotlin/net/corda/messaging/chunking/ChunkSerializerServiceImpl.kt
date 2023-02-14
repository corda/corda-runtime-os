package net.corda.messaging.chunking

import java.nio.ByteBuffer
import java.util.UUID
import net.corda.chunking.ChunkBuilderService
import net.corda.chunking.Constants.Companion.CORDA_MESSAGE_OVERHEAD
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.data.CordaAvroSerializer
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkKey
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messaging.api.chunking.ChunkSerializerService
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import net.corda.v5.crypto.DigestAlgorithmName
import org.slf4j.LoggerFactory

/**
 * Breaks up an object, bytes or record into chunks.
 */
class ChunkSerializerServiceImpl(
    maxAllowedMessageSize: Long,
    private val cordaAvroSerializer: CordaAvroSerializer<Any>,
    private val chunkBuilderService: ChunkBuilderService,
    private val platformDigestService: PlatformDigestService
) : ChunkSerializerService {

    companion object {
        const val INITIAL_PART_NUMBER = 1
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    // chunk size must be smaller than the max allowed message size to allow a buffer for the rest of the message.
    private val maxBytesSize = (maxAllowedMessageSize - CORDA_MESSAGE_OVERHEAD).toInt()
    private val chunkSize = maxBytesSize - CORDA_MESSAGE_OVERHEAD

    override fun generateChunks(avroObject: Any): List<Chunk> {
        logger.debug { "Generating chunks for object of type ${avroObject::class.java}" }
        val bytes = tryToSerialize(avroObject)
        if (bytes == null || bytes.size <= maxBytesSize) {
            return emptyList()
        }
        return generateChunksFromBytes(bytes)
    }

    override fun generateChunkedRecords(producerRecord: CordaProducerRecord<*, *>): List<CordaProducerRecord<*, *>> {
        val serializedKey = tryToSerialize(producerRecord.key)
        val valueBytes = tryToSerialize(producerRecord.value)
        if (serializedKey == null || valueBytes == null || valueBytes.size <= maxBytesSize) {
            return emptyList()
        }

        val chunksToKey = generateChunksFromBytes(valueBytes).associateBy {
            ChunkKey.newBuilder()
                .setPartNumber(it.partNumber)
                .setRealKey(ByteBuffer.wrap(serializedKey))
                .setRequestId(it.requestId)
                .build()
        }

        return chunksToKey.map { CordaProducerRecord(producerRecord.topic, it.key, it.value) }
    }

    override fun generateChunksFromBytes(bytes: ByteArray): List<Chunk> {
        val byteSize = bytes.size
        if (byteSize <= maxBytesSize) {
            logger.debug { "Failed to deserialize bytes or object is too small for chunking" }
            return emptyList()
        }
        val hash = platformDigestService.hash(bytes, DigestAlgorithmName.SHA2_256)
        var partNumber = INITIAL_PART_NUMBER
        val requestId = UUID.randomUUID().toString()

        val chunks = mutableListOf<Chunk>()
        for (offset in bytes.indices step chunkSize) {
            val length = kotlin.math.min(chunkSize, bytes.size - offset)
            val byteBuffer = ByteBuffer.wrap(bytes, offset, length)
            chunks.add(chunkBuilderService.buildChunk(requestId, partNumber++, byteBuffer, offset.toLong()))
        }
        chunks.add(chunkBuilderService.buildFinalChunk(requestId, partNumber, hash, byteSize.toLong()-1))
        logger.trace { "Generating chunks for bytes size $byteSize, chunk id ${chunks.first().requestId}" }

        return chunks
    }

    /**
     * Try to serialize an object. Swallow any exceptions thrown. These will be caught and handled by the kafka client on send
     * @param obj object to serialize
     * @return the serialized object as a ByteArray. Returns null if serialization fails or the object was null.
     */
    private fun tryToSerialize(obj: Any?) : ByteArray? {
        if (obj == null) return null
        return try {
            cordaAvroSerializer.serialize(obj)
        } catch (ex: Throwable) {
            // if serialization is going to fail, let it be handled within the kafka client logic
            return null
        }
    }
}
