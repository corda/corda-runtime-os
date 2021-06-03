package net.corda.libs.kafka.topic.utils.impl

import com.typesafe.config.Config
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

    override fun createTopic(topicTemplate: Config) {
        val newTopic =
            NewTopic(
                topicTemplate.getString("topicName"),
                topicTemplate.getInt("numPartitions"),
                topicTemplate.getInt("replicationFactor").toShort()
            )
        val topicConfigOption = topicTemplate.getConfig("config").entrySet()
            .map { entry -> Pair<String, String>(entry.key, entry.value.toString()) }.toMap()
        newTopic.configs(topicConfigOption)
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

