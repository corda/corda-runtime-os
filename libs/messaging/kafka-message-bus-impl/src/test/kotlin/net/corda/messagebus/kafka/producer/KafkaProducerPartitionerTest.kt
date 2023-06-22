package net.corda.messaging.kafka.subscription.net.corda.messagebus.kafka.producer

import net.corda.data.chunking.ChunkKey
import net.corda.messagebus.kafka.producer.KafkaProducerPartitioner
import org.apache.kafka.common.Cluster
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.util.UUID

class KafkaProducerPartitionerTest {

    private companion object {
        private const val TEST_KEY = "test"
        private const val TEST_TOPIC = "test-topic"
        private const val TEST_VALUE = "foo"
    }

    @Test
    fun `chunk keys are always assigned to the same partition`() {
        val cluster = buildCluster()
        val chunkKey1 = buildChunkKey(1)
        val chunkKey2 = buildChunkKey(2)

        val partitioner = KafkaProducerPartitioner()
        val partition1 = getPartition(partitioner, chunkKey1, chunkKey1.toByteBuffer().array(), cluster)
        val partition2 = getPartition(partitioner, chunkKey2, chunkKey2.toByteBuffer().array(), cluster)
        assertEquals(partition1, partition2)
    }

    @Test
    fun `chunk keys are assigned to the same partition as a non-chunked key with the same string value`() {
        val cluster = buildCluster()
        val chunkKey1 = buildChunkKey(1)

        val partitioner = KafkaProducerPartitioner()
        val partition1 = getPartition(partitioner, chunkKey1, chunkKey1.toByteBuffer().array(), cluster)
        val partition2 = getPartition(partitioner, TEST_KEY, TEST_KEY.toByteArray(), cluster)
        assertEquals(partition1, partition2)
    }

    @Test
    fun `getting the partition for the same chunk key twice gives the same result`() {
        val cluster = buildCluster()
        val chunkKey1 = buildChunkKey(1)

        val partitioner = KafkaProducerPartitioner()
        val partition1 = getPartition(partitioner, chunkKey1, chunkKey1.toByteBuffer().array(), cluster)
        val partition2 = getPartition(partitioner, chunkKey1, chunkKey1.toByteBuffer().array(), cluster)
        assertEquals(partition1, partition2)
    }

    @Test
    fun `flow mapper session event maps to same partition as flow`() {
        val flowId = UUID.randomUUID()
        val sessionId = "#" + flowId.toString().substring(0, 8) + "XXXXXXXX/" + UUID.randomUUID().toString()

        val cluster = buildCluster(setOf("flow.event", "flow.mapper.event"))
        val partitioner = KafkaProducerPartitioner()
        val flowIdPartition = partitioner.partition(
            "flow.event",
            flowId.toString(),
            StringSerializer().serialize("flow.event", flowId.toString()),
            TEST_VALUE,
            TEST_VALUE.toByteArray(),
            cluster
        )
        val sessionIdPartition = partitioner.partition(
            "flow.mapper.event",
            sessionId,
            StringSerializer().serialize("flow.mapper.event", sessionId),
            TEST_VALUE,
            TEST_VALUE.toByteArray(),
            cluster
        )
        assertEquals(flowIdPartition, sessionIdPartition)
    }

    private fun buildChunkKey(chunk: Int): ChunkKey {
        return ChunkKey.newBuilder()
            .setRealKey(ByteBuffer.wrap(TEST_KEY.toByteArray()))
            .setPartNumber(chunk)
            .build()
    }

    private fun getPartition(
        partitioner: KafkaProducerPartitioner,
        key: Any,
        keyBytes: ByteArray,
        cluster: Cluster
    ): Int {
        return partitioner.partition(
            TEST_TOPIC,
            key,
            keyBytes,
            TEST_VALUE,
            TEST_VALUE.toByteArray(),
            cluster
        )
    }

    private fun buildCluster(topics: Set<String> = setOf(TEST_TOPIC)): Cluster {
        val partitions = topics.flatMap { topic ->
            (1..100).map {
                PartitionInfo(topic, it, null, null, null)
            }
        }
        return Cluster("test", setOf(), partitions, setOf(), setOf())
    }
}