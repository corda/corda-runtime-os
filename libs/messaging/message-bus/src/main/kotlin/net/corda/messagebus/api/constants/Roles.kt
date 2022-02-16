package net.corda.messagebus.api.constants

/**
 * Roles are used to describe the job within the patterns library that the constructed consumer or producer should be
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

enum class ProducerRoles {
    PUBLISHER {
        override val configPath = "publisher"
    },
    DURABLE {
        override val configPath = "durable.producer"
    },
    SAE_PRODUCER {
        override val configPath = "stateAndEvent.producer"
    },
    EVENT_LOG {
        override val configPath = "eventLog.producer"
    },
    RPC_SENDER {
        override val configPath = "rpcSender.producer"
    },
    RPC_RESPONDER {
        override val configPath = "rpcResponder.producer"
    };
    abstract val configPath: String
}