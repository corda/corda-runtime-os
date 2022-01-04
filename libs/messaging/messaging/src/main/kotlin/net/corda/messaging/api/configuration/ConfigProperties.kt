package net.corda.messaging.api.configuration

import net.corda.messagebus.api.configuration.ConfigProperties
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.CONSUMER_CLOSE_TIMEOUT
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.CONSUMER_POLL_AND_PROCESS_RETRIES
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.GROUP_ID

class ConfigProperties {
    companion object {
        const val TOPIC = "topic"
        const val DEAD_LETTER_QUEUE_SUFFIX = "topic.deadLetterQueueSuffix"
        const val GROUP = "group"
        const val INSTANCE_ID = "instanceId"
        const val CLIENT_ID_COUNTER = "clientIdCounter"

        const val STATE_CONSUMER = "stateConsumer"
        const val EVENT_CONSUMER = "eventConsumer"

        const val EVENT_GROUP_ID = "$EVENT_CONSUMER.${GROUP_ID}"
        val EVENT_CONSUMER_THREAD_STOP_TIMEOUT = CONSUMER_THREAD_STOP_TIMEOUT.replace("consumer", "eventConsumer")
        val EVENT_CONSUMER_POLL_AND_PROCESS_RETRIES = CONSUMER_POLL_AND_PROCESS_RETRIES.replace("consumer", "eventConsumer")
        val EVENT_CONSUMER_CLOSE_TIMEOUT = CONSUMER_CLOSE_TIMEOUT.replace("consumer", "eventConsumer")

        const val STATE_TOPIC_NAME = "${STATE_CONSUMER}.${ConfigProperties.TOPIC_NAME}"

        const val PATTERN_PUBLISHER = "messaging.pattern.publisher"
        const val PATTERN_PUBSUB = "messaging.pattern.pubsub"
        const val PATTERN_DURABLE = "messaging.pattern.durable"
        const val PATTERN_COMPACTED = "messaging.pattern.compacted"
        const val PATTERN_STATEANDEVENT = "messaging.pattern.stateAndEvent"
        const val PATTERN_EVENTLOG = "messaging.pattern.eventLog"
        const val PATTERN_RANDOMACCESS = "messaging.pattern.randomAccess"
        const val PATTERN_RPC_SENDER = "messaging.pattern.rpcSender"
        const val PATTERN_RPC_RESPONDER = "messaging.pattern.rpcResponder"
    }
}
