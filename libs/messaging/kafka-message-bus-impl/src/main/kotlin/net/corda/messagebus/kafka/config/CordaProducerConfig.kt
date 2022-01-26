package net.corda.messagebus.kafka.config

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.schema.messaging.TOPIC_PREFIX_PATH
import net.corda.messagebus.api.configuration.ConfigProperties
import net.corda.messagebus.getStringOrNull
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import java.time.Duration

data class CordaProducerConfig(
    val clientId: String,
    val transactionalId: String?,
    val topicPrefix: String,
    val closeTimeout: Duration
) {
    companion object {
        fun fromConfig(config: SmartConfig): CordaProducerConfig {
            return CordaProducerConfig(
                config.getString(CommonClientConfigs.CLIENT_ID_CONFIG),
                config.getStringOrNull(ProducerConfig.TRANSACTIONAL_ID_CONFIG),
                config.getString(TOPIC_PREFIX_PATH),
                Duration.ofMillis(config.getLong(ConfigProperties.CLOSE_TIMEOUT))
            )
        }
    }
}
