package net.corda.messaging.api.chunking

import net.corda.data.chunking.Chunk
import net.corda.messagebus.api.producer.CordaProducerRecord

/**
 * Service to handle generating chunks for data that may require chunking
 */
interface ChunkSerializerService {

    /**
     * Take any Avro object, String or ByteArray and divide it into [Chunk]s
     * @param anyObject Object to chunk.
     * @return Returns the object broken up into chunks. Returns an empty list if the object was too small to be chunked or failed to be
     * serialized.
     */
    fun generateChunks(anyObject: Any) : List<Chunk>

    /**
     * Take a messaging [CordaProducerRecord] and divide it into chunks.
     * [Chunk] records will be keyed by an associated [ChunkKey].
     * @param producerRecord Message library [Record] to be chunked.
     * @return Returns the record broken up into chunks. Returns an empty list if the object was too small to be chunked or failed to be
     * serialized.
     */
    fun generateChunkedRecords(producerRecord: CordaProducerRecord<*, *>) : List<CordaProducerRecord<*, *>>
}
