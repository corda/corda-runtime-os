package net.corda.libs.messaging.topic

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.corda.libs.messaging.topic.utils.TopicUtils
import net.corda.messagebus.db.datamodel.TopicEntry
import net.corda.orm.utils.transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.persistence.EntityManagerFactory

/**
 * DB implementation of [TopicUtils]
 * Used to create new topics in a database
 * Exceptions about duplicate topic insertion are ignored
 */
class DBTopicUtils(
    private val entityManagerFactory: EntityManagerFactory
) : TopicUtils {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun createTopics(topicsTemplate: Config) {
        try {
            topicsTemplate.checkValid(referenceTopicsConfig())
        } catch (e: ConfigException) {
            log.warn("Error validating topic configuration")
        }

        val topicTemplateList = topicsTemplate.getObjectList("topics")

        topicTemplateList.forEach { topicTemplateItem ->
            val conf = topicTemplateItem.toConfig()
            val topic = TopicEntry(
                conf.getString("topicName"),
                conf.getInt("numPartitions")
            )

            entityManagerFactory.transaction { entityManager ->
                if (entityManager.find(TopicEntry::class.java, topic.topic) == null) {
                    entityManager.persist(topic)
                }
            }
        }
    }

    private fun referenceTopicsConfig(): Config = ConfigFactory.parseString(
        """
        topics = [
            {
                topicName = "config.topic"
                numPartitions = 1
            }
        ]
        """.trimIndent()
    )
}
