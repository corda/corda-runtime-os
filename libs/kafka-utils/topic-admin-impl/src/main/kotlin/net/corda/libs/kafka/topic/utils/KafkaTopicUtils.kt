package net.corda.libs.kafka.topic.utils

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.errors.TopicExistsException
import org.osgi.service.component.annotations.Component
import java.util.*
import java.util.concurrent.ExecutionException

/**
 * Kafka implementation of [TopicUtils]
 * Used to create new topics on kafka
 * Any [ExecutionException]s apart from [TopicExistsException]s are thrown back to the user
 */
@Component
class KafkaTopicUtils(private val adminClient: AdminClient) : TopicUtils {

    override fun createTopic(
        topicName: String,
        partitions: Int,
        replication: Short
    ) {
        val newTopic = NewTopic(topicName, partitions, replication)
        try {
                adminClient.createTopics(listOf(newTopic)).all().get()
        } catch (e: ExecutionException) {
            if (e.cause !is TopicExistsException) throw e
        }
    }
}

