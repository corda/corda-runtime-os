package net.corda.messaging.api.chunking

import net.corda.data.chunking.Chunk
import net.corda.messagebus.api.producer.CordaProducerRecord

/**
 * Service to handle generating chunks for data that may require chunking
 */
interface ProducerChunkService {

    /**
     * Take a byte array and divide it into [Chunk]s
     * @param bytes Any byte array
     * @return Returns the object broken up into chunks. Returns an empty list if the object was too small to be chunked or failed to be
     * serialized.
     */
    fun generateChunksFromBytes(bytes: ByteArray): List<Chunk>

    /**
     * Take an Avro object and divide it into [Chunk]s
     * @param avroObject Avro object to chunk.
     * @return Returns the object broken up into chunks. Returns an empty list if the object was too small to be chunked or failed to be
     * serialized.
     */
    fun generateChunks(avroObject: Any) : List<Chunk>

    /**
     * Take a messaging [Record] and divide it into chunks.
     * [Chunk] records will be keyed by an associated [ChunkKey].
     * @param producerRecord Message library [Record] to be chunked.
     * @return Returns the record broken up into chunks. Returns an empty list if the object was too small to be chunked or failed to be
     * serialized.
     */
    fun generateChunkedRecords(producerRecord: CordaProducerRecord<*, *>) : List<CordaProducerRecord<*, *>>
}
