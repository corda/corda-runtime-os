package net.corda.messagebus.api.constants

/**
 * Roles are used to describe the job within the patterns library that the constructed consumer should be
 * performing. This allows them to be configured correctly depending on what they are supposed to be doing. The
 * constants here should reflect the paths that the config can be found in enforced/default config files for the various
 * bus implementations.
 */
enum class ConsumerRoles {
    PUBSUB {
        override val configPath = "pubsub.consumer"
    },
    COMPACTED {
        override val configPath = "compacted.consumer"
    },
    DURABLE {
        override val configPath = "durable.consumer"
    },
    SAE_STATE {
        override val configPath = "stateAndEvent.stateConsumer"
    },
    SAE_EVENT {
        override val configPath = "stateAndEvent.eventConsumer"
    },
    EVENT_LOG {
        override val configPath = "eventLog.consumer"
    },
    RPC_SENDER {
        override val configPath = "rpcSender.consumer"
    },
    RPC_RESPONDER {
        override val configPath = "rpcResponder.consumer"
    };
    abstract val configPath: String
}