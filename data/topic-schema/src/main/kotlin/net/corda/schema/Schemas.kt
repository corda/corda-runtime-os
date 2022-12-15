package net.corda.schema

class Schemas {
    companion object {
        /**
         * Messaging topic schema
         *
         * The following is an example schema for topics.  In this case, for a compacted topic, as defined
         * in the [config] section.
         *
         * topics = [
         *     {
         *         topicName = "exampleCompactedTopic"
         *         numPartitions = 1
         *         replicationFactor = 3
         *         config {
         *             cleanup.policy = compact
         *         }
         *     }
         * ]
         */
        const val TOPIC_NAME = "topicName"
        const val NUM_PARTITIONS = "numPartitions"
        const val REPLICATION_FACTOR = "replicationFactor"
        const val TOPIC_CONFIG = "config"

        /**
         * [getStateAndEventDLQTopic] returns the state and event dlq topic
         * [getStateAndEventStateTopic] returns the state and event state topic
         * [getRPCResponseTopic] returns the rpc response topic
         */
        fun getStateAndEventDLQTopic(topic: String) = "$topic.dlq"
        fun getStateAndEventStateTopic(topic: String) = "$topic.state"
        fun getRPCResponseTopic(topic: String) = "$topic.resp"
    }

    /**
     * Config read topic schema
     */
    class Config {
        companion object {
            const val CONFIG_TOPIC = "config.topic"
            const val CONFIG_MGMT_REQUEST_TOPIC = "config.management.request"
            val CONFIG_MGMT_REQUEST_RESP_TOPIC = getRPCResponseTopic(CONFIG_MGMT_REQUEST_TOPIC)
        }
    }

    /**
     * Crypto topic schema
     */
    class Crypto {
        companion object {
            const val HSM_REGISTRATION_MESSAGE_TOPIC = "crypto.registration.hsm"
            const val RPC_OPS_MESSAGE_TOPIC = "crypto.ops.rpc"
            val RPC_OPS_MESSAGE_RESPONSE_TOPIC = getRPCResponseTopic(RPC_OPS_MESSAGE_TOPIC)
            const val RPC_OPS_CLIENT_TOPIC = "crypto.ops.rpc.client"
            const val FLOW_OPS_MESSAGE_TOPIC = "crypto.ops.flow"
            const val HSM_CONFIG_TOPIC = "crypto.config.hsm"
            const val MEMBER_CONFIG_TOPIC = "crypto.config.member"
            const val SIGNING_KEY_PERSISTENCE_TOPIC = "crypto.key.info"
            const val SOFT_HSM_PERSISTENCE_TOPIC = "crypto.key.soft"
            const val EVENT_TOPIC = "crypto.event"
            const val HSM_CONFIGURATION_LABEL_TOPIC = "crypto.hsm.label"
            const val HSM_CONFIGURATION_HSM_LABEL_TOPIC = "crypto.config.hsm.label"
            const val RPC_HSM_REGISTRATION_MESSAGE_TOPIC = "crypto.hsm.rpc.registration"
            val RPC_HSM_REGISTRATION_MESSAGE_RESPONSE_TOPIC = getRPCResponseTopic(RPC_HSM_REGISTRATION_MESSAGE_TOPIC)
            const val RPC_HSM_CONFIGURATION_MESSAGE_TOPIC = "crypto.hsm.rpc.configuration"
            val RPC_HSM_CONFIGURATION_MESSAGE_RESPONSE_TOPIC = getRPCResponseTopic(RPC_HSM_CONFIGURATION_MESSAGE_TOPIC)
        }
    }

    /**
     * Flow event topic schema
     */
    class Flow {
        companion object {
            const val FLOW_STATUS_TOPIC = "flow.status"
            const val FLOW_EVENT_TOPIC = "flow.event"
            val FLOW_EVENT_STATE_TOPIC = getStateAndEventStateTopic(FLOW_EVENT_TOPIC)
            val FLOW_EVENT_DLQ_TOPIC = getStateAndEventDLQTopic(FLOW_EVENT_TOPIC)
            const val FLOW_MAPPER_EVENT_TOPIC = "flow.mapper.event"
            val FLOW_MAPPER_EVENT_STATE_TOPIC = getStateAndEventStateTopic(FLOW_MAPPER_EVENT_TOPIC)
            val FLOW_MAPPER_EVENT_DLQ_TOPIC = getStateAndEventDLQTopic(FLOW_MAPPER_EVENT_TOPIC)
        }
    }

    /**
     * Corda Services topic schema
     */
    class Services{
        companion object {
            const val TOKEN_CACHE_EVENT = "services.token.event"
            val TOKEN_CACHE_EVENT_STATE = getStateAndEventStateTopic(TOKEN_CACHE_EVENT)
            val TOKEN_CACHE_EVENT_DLQ = getStateAndEventDLQTopic(TOKEN_CACHE_EVENT)
        }
    }

    /**
     * Membership topic schema
     */
    class Membership {
        companion object {
            const val GROUP_PARAMETERS_TOPIC = "membership.group.params"

            const val MEMBER_LIST_TOPIC = "membership.members"
            const val MEMBERSHIP_RPC_TOPIC = "membership.rpc.ops"
            val MEMBERSHIP_RPC_RESPONSE_TOPIC = getRPCResponseTopic(MEMBERSHIP_RPC_TOPIC)
            const val MEMBERSHIP_DB_RPC_TOPIC = "membership.db.rpc.ops"
            val MEMBERSHIP_DB_RPC_RESPONSE_TOPIC = getRPCResponseTopic(MEMBERSHIP_DB_RPC_TOPIC)
            const val MEMBERSHIP_STATIC_NETWORK_TOPIC = "membership.static.network"

            const val EVENT_TOPIC = "membership.event"

            const val REGISTRATION_COMMAND_TOPIC = "membership.registration"
            val REGISTRATION_STATE_TOPIC = getStateAndEventStateTopic(REGISTRATION_COMMAND_TOPIC)
            val REGISTRATION_DLQ_TOPIC = getStateAndEventDLQTopic(REGISTRATION_COMMAND_TOPIC)

            const val SYNCHRONIZATION_TOPIC = "membership.sync"
        }
    }

    object Certificates {
        const val CERTIFICATES_RPC_TOPIC = "certificates.rpc.ops"
        val CERTIFICATES_RPC_RESPONSE_TOPIC = getRPCResponseTopic(CERTIFICATES_RPC_TOPIC)
    }

    /**
     * P2P topic schema
     */
    class P2P {
        companion object {
            const val P2P_OUT_TOPIC = "p2p.out"
            const val P2P_OUT_MARKERS = "p2p.out.markers"
            val P2P_OUT_MARKERS_STATE = getStateAndEventStateTopic(P2P_OUT_MARKERS)
            val P2P_OUT_MARKERS_DLQ = getStateAndEventDLQTopic(P2P_OUT_MARKERS)
            const val P2P_IN_TOPIC = "p2p.in"
            const val P2P_HOSTED_IDENTITIES_TOPIC = "p2p.hosted.identities"
            const val LINK_OUT_TOPIC = "link.out"
            const val LINK_IN_TOPIC = "link.in"
            const val SESSION_OUT_PARTITIONS = "session.out.partitions"
            const val GATEWAY_TLS_TRUSTSTORES = "gateway.tls.truststores"
            const val GATEWAY_TLS_CERTIFICATES = "gateway.tls.certs"
            const val GATEWAY_REVOCATION_CHECK_REQUEST_TOPIC = "gateway.revocation.request"
            val GATEWAY_REVOCATION_CHECK_RESPONSE_TOPIC = getRPCResponseTopic(GATEWAY_REVOCATION_CHECK_REQUEST_TOPIC)

            /**
             * Topics for (temporary) stub components.
             */
            const val CRYPTO_KEYS_TOPIC = "p2p.crypto.keys"
            const val GROUP_POLICIES_TOPIC = "p2p.group.policies"
            const val MEMBER_INFO_TOPIC = "p2p.members.info"
        }
    }

    /**
     * Permissions Message schema
     */
    class Permissions {
        companion object {
            const val PERMISSIONS_USER_SUMMARY_TOPIC = "permissions.user.summary"
        }
    }

    /**
     * Persistence Message schema
     */
    class Persistence {
        companion object {
            const val PERSISTENCE_ENTITY_PROCESSOR_TOPIC = "persistence.entity.processor"
            const val PERSISTENCE_LEDGER_PROCESSOR_TOPIC = "persistence.ledger.processor"
        }
    }

    /**
     * RPC Message schema
     */
    class RPC {
        companion object {
            const val RPC_PERM_MGMT_REQ_TOPIC = "rpc.permissions.management"
            val RPC_PERM_MGMT_RESP_TOPIC = getRPCResponseTopic(RPC_PERM_MGMT_REQ_TOPIC)
            const val RPC_PERM_USER_TOPIC = "rpc.permissions.user"
            const val RPC_PERM_GROUP_TOPIC = "rpc.permissions.group"
            const val RPC_PERM_ROLE_TOPIC = "rpc.permissions.role"
            const val RPC_PERM_ENTITY_TOPIC = "rpc.permissions.permission"
        }
    }

    /**
     * Uniqueness checker schema
     */
    class UniquenessChecker {
        companion object {
            const val UNIQUENESS_CHECK_TOPIC = "uniqueness.check"
        }
    }

    /**
     * Virtual Node schema
     */
    class VirtualNode {
        companion object {
            const val VIRTUAL_NODE_INFO_TOPIC = "virtual.node.info"
            const val VIRTUAL_NODE_CREATION_REQUEST_TOPIC = "virtual.node.creation.request"
            val VIRTUAL_NODE_CREATION_REQUEST_RESPONSE_TOPIC = getRPCResponseTopic(VIRTUAL_NODE_CREATION_REQUEST_TOPIC)
            const val CPI_INFO_TOPIC = "cpi.info"
            const val CPI_UPLOAD_TOPIC = "cpi.upload"
            const val CPI_UPLOAD_STATUS_TOPIC = "cpi.upload.status"
            const val CPK_FILE_TOPIC = "cpk.file"
        }
    }
}
