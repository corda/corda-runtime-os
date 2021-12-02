package net.corda.messagebus.api.configuration

import com.typesafe.config.Config
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
            val stateTopic = config.getString(ConfigProperties.STATE_TOPIC_NAME)
            val eventGroupID = config.getString(ConfigProperties.EVENT_GROUP_ID)
            val instanceId = config.getString(ConfigProperties.INSTANCE_ID)
            val loggerName = "$eventGroupID.${config.getString(ConfigProperties.PRODUCER_TRANSACTIONAL_ID)}"
            val producerClientId: String = config.getString(ConfigProperties.PRODUCER_CLIENT_ID)
            val consumerThreadStopTimeout = config.getLong(ConfigProperties.EVENT_CONSUMER_THREAD_STOP_TIMEOUT)
            val consumerCloseTimeout = Duration.ofMillis(config.getLong(ConfigProperties.EVENT_CONSUMER_CLOSE_TIMEOUT))
            val producerCloseTimeout = Duration.ofMillis(config.getLong(ConfigProperties.PRODUCER_CLOSE_TIMEOUT))
            val consumerPollAndProcessMaxRetries = config.getLong(ConfigProperties.EVENT_CONSUMER_POLL_AND_PROCESS_RETRIES)
            val maxPollInterval = config.getLong(ConfigProperties.CONSUMER_MAX_POLL_INTERVAL.replace("consumer", "eventConsumer"))
            val processorTimeout = config.getLong(ConfigProperties.CONSUMER_PROCESSOR_TIMEOUT.replace("consumer", "eventConsumer"))
            val deadLetterQueueSuffix = config.getString(ConfigProperties.DEAD_LETTER_QUEUE_SUFFIX)
            val eventConsumerConfig = config.getConfig(ConfigProperties.EVENT_CONSUMER)
            val stateConsumerConfig = config.getConfig(ConfigProperties.STATE_CONSUMER)
            val producerConfig = config.getConfig(ConfigProperties.KAFKA_PRODUCER)

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
