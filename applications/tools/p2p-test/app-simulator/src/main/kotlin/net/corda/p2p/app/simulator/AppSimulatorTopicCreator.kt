package net.corda.p2p.app.simulator

import com.typesafe.config.ConfigFactory
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.BootConfig
import java.util.Properties

class AppSimulatorTopicCreator(private val bootstrapConfig: SmartConfig, private val topicAdmin: KafkaTopicAdmin)
{
    companion object {
        internal const val APP_RECEIVED_MESSAGES_TOPIC = "p2p.app.received_msg"
        private const val APP_RECEIVED_MESSAGES_PARTITIONS = 10
        private const val APP_RECEIVED_MESSAGES_REPLICATION = 1
        private val APP_RECEIVED_MESSAGE_TOPIC_CONF = mapOf(
            "topics" to listOf(
                mapOf(
                    "topicName" to APP_RECEIVED_MESSAGES_TOPIC,
                    "numPartitions" to APP_RECEIVED_MESSAGES_PARTITIONS,
                    "replicationFactor" to APP_RECEIVED_MESSAGES_REPLICATION,
                    "config" to mapOf("cleanup.policy" to "delete")
                )
            )
        )
    }

    fun createTopic() {
        val topicCreationConfig = ConfigFactory.parseMap(APP_RECEIVED_MESSAGE_TOPIC_CONF)
        topicAdmin.createTopics(
            bootstrapConfig.getConfig(BootConfig.BOOT_KAFKA_COMMON).toKafkaProperties(),
            topicCreationConfig.root().render()
        )
    }

    private fun SmartConfig.toKafkaProperties(): Properties {
        val properties = Properties()
        for ((key, _) in this.entrySet()) {
            properties.setProperty(key, this.getString(key))
        }
        return properties
    }
}