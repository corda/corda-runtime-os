package net.corda.p2p.app.simulator

import com.typesafe.config.ConfigFactory
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.BootConfig
import java.util.Properties

class AppSimulatorTopicCreator(
    private val bootstrapConfig: SmartConfig,
    private val topicAdmin: KafkaTopicAdmin,
    topicCreationParams: TopicCreationParams,
) {
    companion object {
        internal const val APP_RECEIVED_MESSAGES_TOPIC = "p2p.app.received_msg"
    }

    private val appReceivedMessageTopicConf = mapOf(
        "topics" to listOf(
            mapOf(
                "topicName" to APP_RECEIVED_MESSAGES_TOPIC,
                "numPartitions" to topicCreationParams.numPartitions,
                "replicationFactor" to topicCreationParams.replicationFactor,
                "config" to mapOf("cleanup.policy" to "delete"),
            ),
        ),
    )

    fun createTopic() {
        val topicCreationConfig = ConfigFactory.parseMap(appReceivedMessageTopicConf)
        topicAdmin.createTopics(
            bootstrapConfig.getConfig(BootConfig.BOOT_KAFKA_COMMON).toKafkaProperties(),
            topicCreationConfig.root().render(),
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
