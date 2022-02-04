package net.corda.messagebus.kafka.config

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.schema.messaging.TOPIC_PREFIX_PATH
import net.corda.messagebus.api.configuration.ConfigProperties
import org.apache.kafka.clients.CommonClientConfigs
import java.time.Duration

data class CordaConsumerConfig(
    val group: String,
    val topic: String,
    val topicPrefix: String,
    val pollTimeout: Duration,
    val closeTimeout: Duration,
    val subscribeRetries: Long,
    val commitOffsetRetries: Long
) {
    companion object {
        fun fromConfig(config: SmartConfig): CordaConsumerConfig {
            return CordaConsumerConfig(
                config.getString(CommonClientConfigs.GROUP_ID_CONFIG),
                config.getString(ConfigProperties.TOPIC_NAME),
                config.getString(TOPIC_PREFIX_PATH),
                Duration.ofMillis(config.getLong(ConfigProperties.POLL_TIMEOUT)),
                Duration.ofMillis(config.getLong(ConfigProperties.CLOSE_TIMEOUT)),
                config.getLong(ConfigProperties.SUBSCRIBE_MAX_RETRIES),
                config.getLong(ConfigProperties.COMMIT_OFFSET_MAX_RETRIES)
            )
        }
    }
}