package net.corda.libs.kafka.topic.utils.impl

import net.corda.libs.kafka.topic.utils.TopicUtils
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.errors.TopicExistsException
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutionException

/**
 * Kafka implementation of [TopicUtils]
 * Used to create new topics on kafka
 * Any [ExecutionException]s apart from [TopicExistsException]s are thrown back to the user
 */
@Component
class KafkaTopicUtils(private val adminClient: AdminClient) : TopicUtils {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun createTopic(
        topicName: String,
        partitions: Int,
        replication: Short
    ) {
        val newTopic = NewTopic(topicName, partitions, replication)
        try {
            log.info("Attempting to create topic: $newTopic")
            adminClient.createTopics(listOf(newTopic)).all().get()
            log.info("$newTopic created successfully")
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is TopicExistsException -> {
                    log.info("$newTopic already exists")
                }
                null -> throw e
                else -> throw cause
            }
        }
    }

    override fun createCompactedTopic(
        topicName: String,
        partitions: Int,
        replication: Short,
    ) {
        val newTopic = NewTopic(topicName, partitions, replication)
        newTopic.configs(mapOf(Pair("cleanup.policy", "compact")))
        try {
            log.info("Attempting to create topic: $newTopic")
            adminClient.createTopics(listOf(newTopic)).all().get()
            log.info("$newTopic created successfully")
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is TopicExistsException -> {
                    log.info("$newTopic already exists")
                }
                null -> throw e
                else -> throw cause
            }
        }
    }
}

