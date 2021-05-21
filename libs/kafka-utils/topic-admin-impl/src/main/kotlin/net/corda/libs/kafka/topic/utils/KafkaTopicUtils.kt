package net.corda.libs.kafka.topic.utils

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.errors.TopicExistsException
import org.osgi.service.component.annotations.Component
import java.util.*
import java.util.concurrent.ExecutionException

@Component
object KafkaTopicUtils : TopicUtils {
    override fun createTopic(
        topicName: String,
        partitions: Int,
        replication: Short,
        kafkaProps: Properties
    ) {
        val newTopic = NewTopic(topicName, partitions, replication)
        try {
            with(AdminClient.create(kafkaProps)) {
                createTopics(listOf(newTopic)).all().get()
            }
        } catch (e: ExecutionException) {
            if (e.cause !is TopicExistsException) throw e
        }
    }
}

