package net.corda.messaging.kafka.subscription.net.corda.messagebus.kafka.utils

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.kafka.utils.toCordaTopicPartition
import net.corda.messagebus.kafka.utils.toTopicPartition
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class KafkaConversionsTest {
    private val partition = 100
    private val topicName = "topic1"
    private val testTopicPrefix = "test-"

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
