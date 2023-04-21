package net.corda.messagebus.api.constants

/**
 * Roles are used to describe the job within the patterns library that the constructed consumer should be
 * performing. This allows them to be configured correctly depending on what they are supposed to be doing. The
 * constants here should reflect the paths that the config can be found in enforced/default config files for the various
 * bus implementations.
 */
enum class ConsumerRoles(val configPath: String) {
    PUBSUB ("pubsub.consumer"),
    COMPACTED ("compacted.consumer"),
    DURABLE ("durable.consumer"),
    SAE_STATE ("stateAndEvent.stateConsumer"),
    SAE_EVENT ("stateAndEvent.eventConsumer"),
    EVENT_LOG ("eventLog.consumer"),
    RPC_SENDER ("rpcSender.consumer"),
    RPC_RESPONDER("rpcResponder.consumer")
}