package net.corda.messagebus.api.constants

/**
 * Roles are used to describe the job within the patterns library that the constructed consumer or producer should be
 * performing. This allows them to be configured correctly depending on what they are supposed to be doing. The
 * constants here should reflect the paths that the config can be found in enforced/default config files for the various
 * bus implementations.
 */
object Roles {
    const val PUBSUB_CONSUMER = "pubsub.consumer"
    const val COMPACTED_CONSUMER = "compacted.consumer"
    const val DURABLE_CONSUMER = "durable.consumer"
    const val DURABLE_PRODUCER = "durable.producer"
    const val SAE_STATE_CONSUMER = "stateAndEvent.stateConsumer"
    const val SAE_EVENT_CONSUMER = "stateAndEvent.eventConsumer"
    const val SAE_PRODUCER = "stateAndEvent.producer"
    const val EVENT_LOG_CONSUMER = "eventLog.consumer"
    const val EVENT_LOG_PRODUCER = "eventLog.producer"
    const val RPC_SENDER_CONSUMER = "rpcSender.consumer"
    const val RPC_SENDER_PRODUCER = "rpcSender.producer"
    const val RPC_RESPONDER_CONSUMER = "rpcResponder.consumer"
    const val RPC_RESPONDER_PRODUCER = "rpcResponder.producer"
}