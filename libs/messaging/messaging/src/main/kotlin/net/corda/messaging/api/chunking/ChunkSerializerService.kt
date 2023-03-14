package net.corda.messaging.api.chunking

import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkKey
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


    /**
     * Serialize [oldValue] and [newValue] and create ChunkKeys for the objects. If the [newValue] is smaller than the [oldValue]
     * return any chunkKeys that need to cleaned up for the given [key]
     * @param oldValue the previous value for the large object
     * @param newValue the new value for the large object
     * @return Returns the ChunkKeys that should be cleared from the bus as part of the update from [oldValue] to [newValue]. Returns
     * null if there is no ChunkKeys that need to be cleared.
     */
    fun getChunkKeysToClear(key: Any, oldValue: Any?, newValue: Any?) : List<ChunkKey>?
}
