package net.corda.libs.messaging.topic

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.corda.libs.messaging.topic.utils.TopicUtils
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.errors.TopicExistsException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutionException

/**
 * Kafka implementation of [TopicUtils]
 * Used to create new topics on kafka
 * Any [ExecutionException]s apart from [TopicExistsException]s are thrown back to the user
 */
class KafkaTopicUtils(private val adminClient: AdminClient) : TopicUtils {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun createTopics(topicsTemplate: Config) {
        try {
            topicsTemplate.checkValid(referenceTopicsConfig())
        } catch (e: ConfigException) {
            log.warn("Error validating topic configuration, ${e.message}")
        }

        val topicTemplateList = topicsTemplate.getObjectList("topics")
        val topics = mutableListOf<NewTopic>()

        topicTemplateList.forEach { topicTemplateItem ->
            val topicTemplate = topicTemplateItem.toConfig()
            val newTopic =
                NewTopic(
                    topicTemplate.getString("topicName"),
                    topicTemplate.getInt("numPartitions"),
                    topicTemplate.getInt("replicationFactor").toShort()
                )

            if (topicTemplate.hasPath("config")) {
                val topicConfigOption = topicTemplate.getConfig("config").entrySet()
                    .associate { entry -> Pair<String, String>(entry.key, entry.value.unwrapped().toString()) }
                newTopic.configs(topicConfigOption)
            }
            topics.add(newTopic)
        }

        try {
            log.trace { "Attempting to create topics: $topics" }
            adminClient.createTopics(topics).all().get()
            log.trace { "$topics created successfully" }
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is TopicExistsException -> {
                    log.debug { "$topics already exists" }
                }
                null -> throw e
                else -> throw cause
            }
        }
    }

    private fun referenceTopicsConfig(): Config = ConfigFactory.parseString(
        """
        topics = [
            {
                topicName = "config.topic"
                numPartitions = 1
                replicationFactor = 3
                config {
                    cleanup.policy=compact
                }
            }
        ]
        """.trimIndent()
    )
}
