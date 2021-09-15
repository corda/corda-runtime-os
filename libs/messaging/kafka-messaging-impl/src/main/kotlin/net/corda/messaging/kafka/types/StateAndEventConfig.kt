package net.corda.messaging.kafka.types

import com.typesafe.config.Config
import net.corda.messaging.kafka.properties.ConfigProperties
import java.time.Duration

data class StateAndEventConfig(
    val topicPrefix : String,
    val eventTopic: String,
    val stateTopic: String,
    val eventGroupName: String,
    val loggerName: String,
    val producerClientId: String,
    val consumerThreadStopTimeout: Long,
    val consumerCloseTimeout: Duration,
    val producerCloseTimeout: Duration,
    val consumerPollAndProcessMaxRetries: Long,
    val maxPollInterval: Long,
    val listenerTimeout: Long,
    val processorTimeout: Long,
    val deadLetterQueueSuffix: String,
    val stateConsumerConfig: Config,
    val eventConsumerConfig: Config,
    val producerConfig: Config
)
