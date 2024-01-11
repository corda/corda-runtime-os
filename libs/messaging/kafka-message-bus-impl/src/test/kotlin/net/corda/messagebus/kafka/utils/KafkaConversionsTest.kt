package net.corda.messagebus.kafka.utils

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.producer.CordaProducerRecord
import org.apache.avro.util.Utf8
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import java.nio.charset.StandardCharsets

internal class KafkaConversionsTest {
    private val partition = 100
    private val topicName = "topic1"
    private val testTopicPrefix = "test-"

    // @Test
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

    // @Test
    fun toCordaConsumerRecord() {
        val headerData = "h_value".toByteArray(StandardCharsets.UTF_8)
        val kafkaConsumerRecord = ConsumerRecord<Any, Any>(
            "prefix1_topic1",
            1,
            2,
            "key1",
            10
        )

        kafkaConsumerRecord.headers().add("h1", headerData)

        val result = kafkaConsumerRecord.toCordaConsumerRecord<String, Int>("prefix1_")

        assertThat(result.topic).isEqualTo("topic1")
        assertThat(result.partition).isEqualTo(1)
        assertThat(result.offset).isEqualTo(2)
        assertThat(result.key).isEqualTo("key1")
        assertThat(result.value).isEqualTo(10)
        assertThat(result.headers).containsExactly("h1" to "h_value")
    }

    // @Test
    fun `toCordaConsumerRecord null record value`() {
        val headerData = "h_value".toByteArray(StandardCharsets.UTF_8)
        val kafkaConsumerRecord = ConsumerRecord<Any, Any>(
            "prefix1_topic1",
            1,
            2,
            "key1",
            null
        )

        kafkaConsumerRecord.headers().add("h1", headerData)

        val result = kafkaConsumerRecord.toCordaConsumerRecord<String, Int>("prefix1_")

        assertThat(result.topic).isEqualTo("topic1")
        assertThat(result.partition).isEqualTo(1)
        assertThat(result.offset).isEqualTo(2)
        assertThat(result.key).isEqualTo("key1")
        assertThat(result.value).isNull()
        assertThat(result.headers).containsExactly("h1" to "h_value")
    }

    // @Test
    fun `toCordaConsumerRecord with key and value`() {
        val headerData = "h_value".toByteArray(StandardCharsets.UTF_8)
        val kafkaConsumerRecord = ConsumerRecord<Any, Any>(
            "prefix1_topic1",
            1,
            2,
            "",
            0
        )

        kafkaConsumerRecord.headers().add("h1", headerData)

        val result = kafkaConsumerRecord.toCordaConsumerRecord<String, Int>("prefix1_", "key1", 10)

        assertThat(result.topic).isEqualTo("topic1")
        assertThat(result.partition).isEqualTo(1)
        assertThat(result.offset).isEqualTo(2)
        assertThat(result.key).isEqualTo("key1")
        assertThat(result.value).isEqualTo(10)
        assertThat(result.headers).containsExactly("h1" to "h_value")
    }

    // @Test
    fun `toCordaConsumerRecord with key and null value`() {
        val headerData = "h_value".toByteArray(StandardCharsets.UTF_8)
        val kafkaConsumerRecord = ConsumerRecord<Any, Any>(
            "prefix1_topic1",
            1,
            2,
            "",
            0
        )

        kafkaConsumerRecord.headers().add("h1", headerData)

        val result = kafkaConsumerRecord.toCordaConsumerRecord<String, Int>("prefix1_", "key1", null)

        assertThat(result.topic).isEqualTo("topic1")
        assertThat(result.partition).isEqualTo(1)
        assertThat(result.offset).isEqualTo(2)
        assertThat(result.key).isEqualTo("key1")
        assertThat(result.value).isNull()
        assertThat(result.headers).containsExactly("h1" to "h_value")
    }

    // @Test
    fun toTopicPartitionAddsPrefixToTopic() {
        val cordaTopicPartition = CordaTopicPartition(topicName, partition)
        val topicPartition = cordaTopicPartition.toTopicPartition(testTopicPrefix)

        assertThat(topicPartition.partition()).isEqualTo(partition)
        assertThat(topicPartition.topic()).isEqualTo("$testTopicPrefix$topicName")
    }

    // @Test
    fun toTopicPartitionDoesNotAddPrefixToTopicWhenPrefixAlreadyAdded() {
        val cordaTopicPartition = CordaTopicPartition("$testTopicPrefix$topicName", partition)
        val topicPartition = cordaTopicPartition.toTopicPartition(testTopicPrefix)

        assertThat(topicPartition.partition()).isEqualTo(partition)
        assertThat(topicPartition.topic()).isEqualTo("$testTopicPrefix$topicName")
    }

    // @Test
    fun toCordaTopicPartitionRemovesPrefixFromTopic() {
        val topicPartition = TopicPartition("$testTopicPrefix$topicName", partition)
        val cordaTopicPartition = topicPartition.toCordaTopicPartition(testTopicPrefix)

        assertThat(cordaTopicPartition.partition).isEqualTo(partition)
        assertThat(cordaTopicPartition.topic).isEqualTo(topicName)
    }

    // @Test
    fun toCordaTopicPartitionDoesNotRemoveAnythingWhenPrefixIsNotFound() {
        val topicPartition = TopicPartition(topicName, partition)
        val cordaTopicPartition = topicPartition.toCordaTopicPartition(testTopicPrefix)

        assertThat(cordaTopicPartition.partition).isEqualTo(partition)
        assertThat(cordaTopicPartition.topic).isEqualTo(topicName)
    }
}
