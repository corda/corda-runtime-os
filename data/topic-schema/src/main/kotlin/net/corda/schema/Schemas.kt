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
         * Flow event topic schema
         * Note that this follows a state + event pattern
         *
         * topics = [
         *     {
         *         topicName = "flow.event"
         *         numPartitions = 3
         *         replicationFactor = 3
         *     },
         *     {
         *         topicName = "flow.event.DLQ"
         *         numPartitions = $numPartitions
         *         replicationFactor = $replicationFactor
         *     },
         *     {
         *         topicName = "flow.event.state"
         *         numPartitions = 3
         *         replicationFactor = 3,
         *         config {
         *             cleanup.policy=compact
         *         }
         *     }
         * ]
         */
        const val FLOW_EVENT_TOPIC = "flow.event"
        const val FLOW_MAPPER_EVENT_TOPIC = "flow.mapper.event"

        fun getStateAndEventDLQTopic(topic: String) = "$topic.DLQ"
        fun getStateAndEventStateTopic(topic: String) = "$topic.state"

        /**
         * Config read topic schema
         * Note that this follows a compacted pattern
         *
         * topics = [
         *     {
         *         topicName = "compactedTopic"
         *         numPartitions = 1
         *         replicationFactor = 3,
         *         config {
         *             cleanup.policy=compact
         *         }
         *     }
         * ]
         */
        const val CONFIG_TOPIC = "config.topic"

        /**
         * P2P topic schema
         */
        const val P2P_OUT_TOPIC = "p2p.out"
        const val P2P_IN_TOPIC = "p2p.in"
        const val P2P_OUT_MARKERS = "p2p.out.markers"
        const val LINK_OUT_TOPIC = "link.out"
        const val LINK_IN_TOPIC = "link.in"
        const val SESSION_OUT_PARTITIONS = "session.out.partitions"

        /**
         * RPC Message schema
         */
        // RPC Permissions
        const val RPC_PERM_MGMT_REQ_TOPIC = "rpc.permissions.management"
        const val RPC_PERM_MGMT_RESP_TOPIC = "rpc.permissions.management.resp"
        const val RPC_PERM_USER_TOPIC = "rpc.permissions.user"
        const val RPC_PERM_GROUP_TOPIC = "rpc.permissions.group"
        const val RPC_PERM_ROLE_TOPIC = "rpc.permissions.role"

        /**
         * Virtual Node schema
         */
        const val VIRTUAL_NODE_INFO_TOPIC = "virtual.node.info"
        const val CPI_INFO_TOPIC = "cpi.info"

        fun getRPCResponseTopic(topic: String) = "$topic.resp"
    }

    /**
     * Crypto topic schema
     */
    class Crypto {
        companion object {
            const val HSM_REGISTRATION_MESSAGE_TOPIC = "crypto.registration.hsm"
            const val RPC_OPS_MESSAGE_TOPIC = "crypto.ops.rpc"
            const val FLOW_OPS_MESSAGE_TOPIC = "crypto.ops.flow"
            const val HSM_CONFIG_TOPIC = "crypto.config.hsm"
            const val HSM_CONFIG_LABEL_TOPIC = "crypto.config.hsm.label"
            const val MEMBER_CONFIG_TOPIC = "crypto.config.member"
            const val SIGNING_KEY_PERSISTENCE_TOPIC = "crypto.key.info"
            const val SOFT_HSM_PERSISTENCE_TOPIC = "crypto.key.soft"
            const val EVENT_TOPIC = "crypto.event"
        }
    }

    /**
     * Membership topic schema
     */
    class Membership {
        companion object {
            // Member persistence topics
            const val MEMBER_LIST_TOPIC = "membership.members"
            const val GROUP_PARAMETERS_TOPIC = "membership.group.params"
            const val CPI_WHITELIST_TOPIC = "membership.group.cpi.whitelists"
            const val PROPOSAL_TOPIC = "membership.proposals"

            // Member messaging topics
            const val UPDATE_TOPIC = "membership.update"
            const val EVENT_TOPIC = "membership.event"
        }
    }
}
