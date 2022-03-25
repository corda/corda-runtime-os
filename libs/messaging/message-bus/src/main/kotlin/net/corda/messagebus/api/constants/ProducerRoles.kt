package net.corda.messagebus.api.constants

/**
 * Roles are used to describe the job within the patterns library that the constructed producer should be
 * performing. This allows them to be configured correctly depending on what they are supposed to be doing. The
 * constants here should reflect the paths that the config can be found in enforced/default config files for the various
 * bus implementations.
 */
enum class ProducerRoles(val configPath: String) {
    PUBLISHER("publisher.producer"),
    DURABLE("durable.producer"),
    SAE_PRODUCER("stateAndEvent.producer"),
    EVENT_LOG("eventLog.producer"),
    RPC_SENDER("rpcSender.producer"),
    RPC_RESPONDER("rpcResponder.producer")
}