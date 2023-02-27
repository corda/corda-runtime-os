package net.corda.messagebus.kafka.producer

import net.corda.data.chunking.ChunkKey
import net.corda.utilities.copyBytes
import net.corda.utilities.trace
import org.apache.kafka.clients.producer.Partitioner
import org.apache.kafka.clients.producer.internals.BuiltInPartitioner
import org.apache.kafka.common.Cluster
import org.slf4j.LoggerFactory

/**
 * Custom partitioner to be used with Kafka Producers.
 */
class KafkaProducerPartitioner : Partitioner {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun configure(configs: MutableMap<String, *>?) {}
    override fun close() {}

    /**
     * To ensure chunked records have unique keys, the real key is wrapped in a ChunkKey object.
     * This means kafka compaction will not delete chunks as the keys will be different.
     * However we still want the records to end up on the same partition so when the key is a ChunkKey we pass the real key bytes to the
     * kafka built in partitioner logic. This function is what is called by default within the normal kafkaProducers.
     * @param topic topic
     * @param key the key of the record. Could be the real key or an object of type ChunkKey
     * @param keyBytes bytes of the key
     * @param value value of the record
     * @param valueBytes serialized value bytes
     * @param cluster cluster object provided by kafka client with cluster information
     * @return partition to send record to
     */
    override fun partition(topic: String, key: Any, keyBytes: ByteArray, value: Any?, valueBytes: ByteArray?, cluster: Cluster): Int {
        val keyBytesToPartition = if (key is ChunkKey) {
            logger.trace { "Found ChunkKey. Using real bytes $keyBytes for partitioning" }
            key.realKey.copyBytes()
        } else {
            keyBytes
        }
        val partition = BuiltInPartitioner.partitionForKey(keyBytesToPartition, cluster.partitionsForTopic(topic).size)
        logger.trace { "Sending record with key $key to partition $partition" }
        return partition
    }
}
