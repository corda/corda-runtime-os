package net.corda.messagebus.api.constants

/**
 * Roles are used to describe the job within the patterns library that the constructed producer should be
 * performing. This allows them to be configured correctly depending on what they are supposed to be doing. The
 * constants here should reflect the paths that the config can be found in enforced/default config files for the various
 * bus implementations.
 */
enum class ProducerRoles {
    PUBLISHER {
        override val configPath = "publisher.producer"
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