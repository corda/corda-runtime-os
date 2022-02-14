package net.corda.messagebus.kafka.config

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.schema.messaging.TOPIC_PREFIX_PATH
import net.corda.messagebus.api.configuration.ConfigProperties
import org.apache.kafka.clients.CommonClientConfigs

data class ConsumerConfig(
    val group: String,
    val topicPrefix: String,
    val subscribeRetries: Long,
    val commitOffsetRetries: Long
) {
    companion object {
        fun fromConfig(config: SmartConfig): ConsumerConfig {
            return ConsumerConfig(
                config.getString(CommonClientConfigs.GROUP_ID_CONFIG),
                config.getString(TOPIC_PREFIX_PATH),
                config.getLong(ConfigProperties.SUBSCRIBE_MAX_RETRIES),
                config.getLong(ConfigProperties.COMMIT_OFFSET_MAX_RETRIES)
            )
        }
    }
}