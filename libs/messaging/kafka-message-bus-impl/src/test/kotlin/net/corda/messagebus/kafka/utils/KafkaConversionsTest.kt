package net.corda.messagebus.kafka.utils

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.producer.CordaProducerRecord
import org.apache.avro.util.Utf8
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class KafkaConversionsTest {
    private val partition = 100
    private val topicName = "topic1"
    private val testTopicPrefix = "test-"

    @Test
    fun toKafkaRecordAddsPrefixAndHeaders() {
        val prefix = "p_"
        val topic = "topic1"
        val headers = listOf("a" to "a_value", "b" to "b_value")
        val record = CordaProducerRecord(topic, "key", "value", headers)

        val result = record.toKafkaRecord(prefix)

        val expectedHeaderAValue = Utf8.getBytesFor("a_value")
        val expectedHeaderBValue = Utf8.getBytesFor("b_value")
        assertThat(result.topic()).isEqualTo("p_topic1")
        assertThat(result.key()).isEqualTo("key")
        assertThat(result.value()).isEqualTo("value")
        assertThat(result.headers().headers("a").single().value()).isEqualTo(expectedHeaderAValue)
        assertThat(result.headers().headers("b").single().value()).isEqualTo(expectedHeaderBValue)
    }

    @Test
    fun toTopicPartitionAddsPrefixToTopic() {
        val cordaTopicPartition = CordaTopicPartition(topicName, partition)
        val topicPartition = cordaTopicPartition.toTopicPartition(testTopicPrefix)

        assertThat(topicPartition.partition()).isEqualTo(partition)
        assertThat(topicPartition.topic()).isEqualTo("$testTopicPrefix$topicName")
    }

    @Test
    fun toTopicPartitionDoesNotAddPrefixToTopicWhenPrefixAlreadyAdded() {
        val cordaTopicPartition = CordaTopicPartition("$testTopicPrefix$topicName", partition)
        val topicPartition = cordaTopicPartition.toTopicPartition(testTopicPrefix)

        assertThat(topicPartition.partition()).isEqualTo(partition)
        assertThat(topicPartition.topic()).isEqualTo("$testTopicPrefix$topicName")
    }

    @Test
    fun toCordaTopicPartitionRemovesPrefixFromTopic() {
        val topicPartition = TopicPartition("$testTopicPrefix$topicName", partition)
        val cordaTopicPartition = topicPartition.toCordaTopicPartition(testTopicPrefix)

        assertThat(cordaTopicPartition.partition).isEqualTo(partition)
        assertThat(cordaTopicPartition.topic).isEqualTo(topicName)
    }

    @Test
    fun toCordaTopicPartitionDoesNotRemoveAnythingWhenPrefixIsNotFound() {
        val topicPartition = TopicPartition(topicName, partition)
        val cordaTopicPartition = topicPartition.toCordaTopicPartition(testTopicPrefix)

        assertThat(cordaTopicPartition.partition).isEqualTo(partition)
        assertThat(cordaTopicPartition.topic).isEqualTo(topicName)
    }
}
