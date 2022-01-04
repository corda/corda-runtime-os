package net.corda.messaging.subscription.config

import com.typesafe.config.Config
import net.corda.messagebus.api.configuration.ConfigProperties
import net.corda.messaging.api.configuration.ConfigProperties.Companion.DEAD_LETTER_QUEUE_SUFFIX
import net.corda.messaging.api.configuration.ConfigProperties.Companion.EVENT_CONSUMER
import net.corda.messaging.api.configuration.ConfigProperties.Companion.EVENT_CONSUMER_CLOSE_TIMEOUT
import net.corda.messaging.api.configuration.ConfigProperties.Companion.EVENT_CONSUMER_POLL_AND_PROCESS_RETRIES
import net.corda.messaging.api.configuration.ConfigProperties.Companion.EVENT_CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messaging.api.configuration.ConfigProperties.Companion.EVENT_GROUP_ID
import net.corda.messaging.api.configuration.ConfigProperties.Companion.STATE_CONSUMER
import net.corda.messaging.api.configuration.ConfigProperties.Companion.STATE_TOPIC_NAME
import java.time.Duration

data class StateAndEventConfig(
    val topicPrefix : String,
    val eventTopic: String,
    val stateTopic: String,
    val eventGroupName: String,
    val instanceId: String,
    val loggerName: String,
    val producerClientId: String,
    val consumerThreadStopTimeout: Long,
    val consumerCloseTimeout: Duration,
    val producerCloseTimeout: Duration,
    val consumerPollAndProcessMaxRetries: Long,
    val maxPollInterval: Long,
    val processorTimeout: Long,
    val deadLetterQueueSuffix: String,
    val stateConsumerConfig: Config,
    val eventConsumerConfig: Config,
    val producerConfig: Config
) {
    companion object {
        fun getStateAndEventConfig(config: Config): StateAndEventConfig {
            val topicPrefix = config.getString(ConfigProperties.TOPIC_PREFIX)
            val eventTopic = config.getString(ConfigProperties.TOPIC_NAME)
            val stateTopic = config.getString(STATE_TOPIC_NAME)
            val eventGroupID = config.getString(EVENT_GROUP_ID)
            val instanceId = config.getString(ConfigProperties.INSTANCE_ID)
            val loggerName = "$eventGroupID.${config.getString(ConfigProperties.PRODUCER_TRANSACTIONAL_ID)}"
            val producerClientId: String = config.getString(ConfigProperties.PRODUCER_CLIENT_ID)
            val consumerThreadStopTimeout = config.getLong(EVENT_CONSUMER_THREAD_STOP_TIMEOUT)
            val consumerCloseTimeout = Duration.ofMillis(config.getLong(EVENT_CONSUMER_CLOSE_TIMEOUT))
            val producerCloseTimeout = Duration.ofMillis(config.getLong(ConfigProperties.PRODUCER_CLOSE_TIMEOUT))
            val consumerPollAndProcessMaxRetries = config.getLong(EVENT_CONSUMER_POLL_AND_PROCESS_RETRIES)
            val maxPollInterval = config.getLong(ConfigProperties.CONSUMER_MAX_POLL_INTERVAL.replace("consumer", "eventConsumer"))
            val processorTimeout = config.getLong(ConfigProperties.CONSUMER_PROCESSOR_TIMEOUT.replace("consumer", "eventConsumer"))
            val deadLetterQueueSuffix = config.getString(DEAD_LETTER_QUEUE_SUFFIX)
            val eventConsumerConfig = config.getConfig(EVENT_CONSUMER)
            val stateConsumerConfig = config.getConfig(STATE_CONSUMER)
            val producerConfig = config.getConfig(ConfigProperties.CORDA_PRODUCER)

            return StateAndEventConfig(
                topicPrefix,
                eventTopic,
                stateTopic,
                eventGroupID,
                instanceId,
                loggerName,
                producerClientId,
                consumerThreadStopTimeout,
                consumerCloseTimeout,
                producerCloseTimeout,
                consumerPollAndProcessMaxRetries,
                maxPollInterval,
                processorTimeout,
                deadLetterQueueSuffix,
                stateConsumerConfig,
                eventConsumerConfig,
                producerConfig
            )
        }
    }
}
