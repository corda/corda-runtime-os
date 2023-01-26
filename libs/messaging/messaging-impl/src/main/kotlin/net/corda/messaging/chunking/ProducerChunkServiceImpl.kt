package net.corda.messaging.chunking

import java.nio.ByteBuffer
import java.util.UUID
import net.corda.chunking.Checksum
import net.corda.chunking.ChunkBuilderService
import net.corda.chunking.Constants.Companion.CORDA_MESSAGE_OVERHEAD
import net.corda.data.CordaAvroSerializer
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkKey
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messaging.api.chunking.ProducerChunkService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

/**
 * Chunks up an object, bytes or record into chunks.
 */
class ProducerChunkServiceImpl(
    maxAllowedMessageSize: Long,
    private val cordaAvroSerializer: CordaAvroSerializer<Any>,
    private val chunkBuilderService: ChunkBuilderService,
) : ProducerChunkService {

    companion object {
        private val logger = contextLogger()
    }

    // chunk size must be smaller than the max allowed message size to allow a buffer for the rest of the message.
    private val chunkSize = (maxAllowedMessageSize - CORDA_MESSAGE_OVERHEAD).toInt()

    override fun generateChunks(avroObject: Any): List<Chunk> {
        logger.debug { "Generating chunks for object of type ${avroObject::class.java}" }
        val bytes = tryToSerialize(avroObject)
        if (bytes == null || bytes.size < chunkSize) {
            return emptyList()
        }
        return generateChunksFromBytes(bytes)
    }

    override fun generateChunkedRecords(producerRecord: CordaProducerRecord<*, *>): List<CordaProducerRecord<*, *>> {
        val serializedKey = tryToSerialize(producerRecord.key)
        val valueBytes = tryToSerialize(producerRecord.value)
        if (serializedKey == null || valueBytes == null || valueBytes.size < chunkSize) {
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
        if (bytes.size < chunkSize) {
            logger.debug { "Failed to deserialize bytes or object is too small for chunking" }
            return emptyList()
        }
        val chunksAsBytes = divideDataIntoChunks(bytes, chunkSize)
        val hash = Checksum.digestForBytes(bytes)
        var partNumber = 0
        val requestId = UUID.randomUUID().toString()
        return chunksAsBytes.map {
            chunkBuilderService.buildChunk(requestId, partNumber++, ByteBuffer.wrap(it))
        }.toMutableList().apply {
            add(chunkBuilderService.buildFinalChunk(requestId, partNumber++, hash))
        }
    }

    /**
     * Split [bytes] into multiple ByteArrays with a max size of [chunkSize]
     */
    private fun divideDataIntoChunks(bytes: ByteArray, chunkSize: Int): ArrayList<ByteArray> {
        val result: ArrayList<ByteArray> = ArrayList()
        if (chunkSize <= 0) {
            result.add(bytes)
        } else {
            for (chunk in bytes.indices step chunkSize) {
                result.add(bytes.copyOfRange(chunk, kotlin.math.min(chunk + chunkSize, bytes.size)))
            }
        }
        return result
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
            // if serialization is going to fail, let it be handled within the consumer logic
            return null
        }
    }
}
