package net.corda.schema;

import org.jetbrains.annotations.NotNull;

public final class Schemas {
    private Schemas() {
    }

    /**
     * Messaging topic schema
     * <p>
     * The following is an example schema for topics.  In this case, for a compacted topic, as defined
     * in the {@code config} section.
     * <pre>{@code
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
     * }</pre>
     */
     public static final String TOPIC_NAME = "topicName";
     public static final String NUM_PARTITIONS = "numPartitions";
     public static final String REPLICATION_FACTOR = "replicationFactor";
     public static final String TOPIC_CONFIG = "config";

    /**
     * @return the state and event dlq topic
     */
    @NotNull
    public static String getStateAndEventDLQTopic(@NotNull String topic) {
        return topic + ".dlq";
    }

    /**
     * @return the state and event state topic
     */
    @NotNull
    public static String getStateAndEventStateTopic(@NotNull String topic) {
        return topic + ".state";
    }

    /**
     * @return the rpc response topic
     */
    @NotNull
    public static String getRPCResponseTopic(@NotNull String topic) {
        return topic + ".resp";
    }

    /**
     * Config read topic schema
     */
    public static final class Config {
        private Config() {
        }

        public static final String CONFIG_TOPIC = "config.topic";
        public static final String CONFIG_MGMT_REQUEST_TOPIC = "config.management.request";
        public static final String CONFIG_MGMT_REQUEST_RESP_TOPIC = getRPCResponseTopic(CONFIG_MGMT_REQUEST_TOPIC);
    }

    /**
     * Crypto topic schema
     */
    public static final class Crypto {
        private Crypto() {
        }

        public static final String RPC_HSM_REGISTRATION_MESSAGE_TOPIC = "crypto.hsm.rpc.registration";
        public static final String RPC_HSM_REGISTRATION_MESSAGE_RESPONSE_TOPIC = getRPCResponseTopic(RPC_HSM_REGISTRATION_MESSAGE_TOPIC);
        public static final String FLOW_OPS_MESSAGE_TOPIC = "crypto.ops.flow";
        public static final String RPC_OPS_MESSAGE_TOPIC = "crypto.ops.rpc";
        public static final String RPC_OPS_MESSAGE_RESPONSE_TOPIC = getRPCResponseTopic(RPC_OPS_MESSAGE_TOPIC);
    }

    /**
     * Flow event topic schema
     */
    public static final class Flow {
        private Flow() {
        }

        public static final String FLOW_STATUS_TOPIC = "flow.status";
        public static final String FLOW_EVENT_TOPIC = "flow.event";
        public static final String FLOW_EVENT_STATE_TOPIC = getStateAndEventStateTopic(FLOW_EVENT_TOPIC);
        public static final String FLOW_EVENT_DLQ_TOPIC = getStateAndEventDLQTopic(FLOW_EVENT_TOPIC);
        public static final String FLOW_MAPPER_EVENT_TOPIC = "flow.mapper.event";
        public static final String FLOW_MAPPER_EVENT_STATE_TOPIC = getStateAndEventStateTopic(FLOW_MAPPER_EVENT_TOPIC);
        public static final String FLOW_MAPPER_EVENT_DLQ_TOPIC = getStateAndEventDLQTopic(FLOW_MAPPER_EVENT_TOPIC);
    }

    /**
     * Corda Services topic schema
     */
    public static final class Services {
        private Services() {
        }

        public static final String TOKEN_CACHE_EVENT = "services.token.event";
        public static final String TOKEN_CACHE_EVENT_STATE = getStateAndEventStateTopic(TOKEN_CACHE_EVENT);
        public static final String TOKEN_CACHE_EVENT_DLQ = getStateAndEventDLQTopic(TOKEN_CACHE_EVENT);

        public static final String TOKEN_CACHE_SYNC_EVENT = "services.token.sync.event";
        public static final String TOKEN_CACHE_SYNC_EVENT_STATE = getStateAndEventStateTopic(TOKEN_CACHE_SYNC_EVENT);
        public static final String TOKEN_CACHE_SYNC_EVENT_DLQ = getStateAndEventDLQTopic(TOKEN_CACHE_SYNC_EVENT);
    }

    /**
     * Membership topic schema
     */
    public static final class Membership {
        private Membership() {
        }

        public static final String GROUP_PARAMETERS_TOPIC = "membership.group.params";

        public static final String MEMBER_LIST_TOPIC = "membership.members";
        public static final String MEMBERSHIP_RPC_TOPIC = "membership.rpc.ops";
        public static final String MEMBERSHIP_RPC_RESPONSE_TOPIC = getRPCResponseTopic(MEMBERSHIP_RPC_TOPIC);
        public static final String MEMBERSHIP_DB_RPC_TOPIC = "membership.db.rpc.ops";
        public static final String MEMBERSHIP_DB_RPC_RESPONSE_TOPIC = getRPCResponseTopic(MEMBERSHIP_DB_RPC_TOPIC);
        public static final String MEMBERSHIP_STATIC_NETWORK_TOPIC = "membership.static.network";
        public static final String MEMBERSHIP_ASYNC_REQUEST_TOPIC = "membership.async.request";
        public static final String MEMBERSHIP_ASYNC_REQUEST_STATE_TOPIC = getStateAndEventStateTopic(MEMBERSHIP_ASYNC_REQUEST_TOPIC);
        public static final String MEMBERSHIP_ASYNC_REQUEST_DLQ_TOPIC = getStateAndEventDLQTopic(MEMBERSHIP_ASYNC_REQUEST_TOPIC);

        public static final String EVENT_TOPIC = "membership.event";

        public static final String REGISTRATION_COMMAND_TOPIC = "membership.registration";
        public static final String REGISTRATION_STATE_TOPIC = getStateAndEventStateTopic(REGISTRATION_COMMAND_TOPIC);
        public static final String REGISTRATION_DLQ_TOPIC = getStateAndEventDLQTopic(REGISTRATION_COMMAND_TOPIC);

        public static final String SYNCHRONIZATION_TOPIC = "membership.sync";
    }

    public static final class Certificates {
        private Certificates() {
        }

        public static final String CERTIFICATES_RPC_TOPIC = "certificates.rpc.ops";
        public static final String CERTIFICATES_RPC_RESPONSE_TOPIC = getRPCResponseTopic(CERTIFICATES_RPC_TOPIC);
    }

    /**
     * P2P topic schema
     */
    public static final class P2P {
        private P2P() {
        }

        public static final String P2P_OUT_TOPIC = "p2p.out";
        public static final String P2P_OUT_MARKERS = "p2p.out.markers";
        public static final String P2P_OUT_MARKERS_STATE = getStateAndEventStateTopic(P2P_OUT_MARKERS);
        public static final String P2P_OUT_MARKERS_DLQ = getStateAndEventDLQTopic(P2P_OUT_MARKERS);
        public static final String P2P_IN_TOPIC = "p2p.in";
        public static final String P2P_HOSTED_IDENTITIES_TOPIC = "p2p.hosted.identities";
        public static final String P2P_MGM_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS = "p2p.mgm.allowed.client.certificate.subjects";
        public static final String LINK_OUT_TOPIC = "link.out";
        public static final String LINK_IN_TOPIC = "link.in";
        public static final String SESSION_OUT_PARTITIONS = "session.out.partitions";
        public static final String GATEWAY_TLS_TRUSTSTORES = "gateway.tls.truststores";
        public static final String GATEWAY_TLS_CERTIFICATES = "gateway.tls.certs";
        public static final String GATEWAY_REVOCATION_CHECK_REQUEST_TOPIC = "gateway.revocation.request";
        public static final String GATEWAY_REVOCATION_CHECK_RESPONSE_TOPIC = getRPCResponseTopic(GATEWAY_REVOCATION_CHECK_REQUEST_TOPIC);
        public static final String P2P_MTLS_MEMBER_CLIENT_CERTIFICATE_SUBJECT_TOPIC = "p2p.mtls.member.client.certificate.subject";
        public static final String GATEWAY_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS = "gateway.allowed.client.certificate.subjects";
    }

    /**
     * Permissions Message schema
     */
    public static final class Permissions {
        private Permissions() {
        }

        public static final String PERMISSIONS_USER_SUMMARY_TOPIC = "permissions.user.summary";
    }

    /**
     * Persistence Message schema
     */
    public static final class Persistence {
        private Persistence() {
        }

        public static final String PERSISTENCE_ENTITY_PROCESSOR_TOPIC = "persistence.entity.processor";
        public static final String PERSISTENCE_LEDGER_PROCESSOR_TOPIC = "persistence.ledger.processor";
    }

    /**
     * Rest Message schema
     */
    public static final class Rest {
        private Rest() {
        }

        public static final String REST_PERM_MGMT_REQ_TOPIC = "rest.permissions.management";
        public static final String REST_PERM_MGMT_RESP_TOPIC = getRPCResponseTopic(REST_PERM_MGMT_REQ_TOPIC);
        public static final String REST_PERM_USER_TOPIC = "rest.permissions.user";
        public static final String REST_PERM_GROUP_TOPIC = "rest.permissions.group";
        public static final String REST_PERM_ROLE_TOPIC = "rest.permissions.role";
        public static final String REST_PERM_ENTITY_TOPIC = "rest.permissions.permission";
    }

    /**
     * Uniqueness checker schema
     */
    public static final class UniquenessChecker {
        private UniquenessChecker() {
        }

        public static final String UNIQUENESS_CHECK_TOPIC = "uniqueness.check";
    }

    /**
     * Verification Message schema
     */
    public static final class Verification {
        private Verification() {
        }

        public static final String VERIFICATION_LEDGER_PROCESSOR_TOPIC = "verification.ledger.processor";
    }

    /**
     * Virtual Node schema
     */
    public static final class VirtualNode {
        private VirtualNode() {
        }

        public static final String VIRTUAL_NODE_INFO_TOPIC = "virtual.node.info";
        public static final String VIRTUAL_NODE_ASYNC_REQUEST_TOPIC = "virtual.node.async.request";
        public static final String VIRTUAL_NODE_OPERATION_STATUS_TOPIC = "virtual.node.operation.status";
        public static final String VIRTUAL_NODE_CREATION_REQUEST_TOPIC = "virtual.node.creation.request";
        public static final String VIRTUAL_NODE_CREATION_REQUEST_RESPONSE_TOPIC = getRPCResponseTopic(VIRTUAL_NODE_CREATION_REQUEST_TOPIC);
        public static final String CPI_INFO_TOPIC = "cpi.info";
        public static final String CPI_UPLOAD_TOPIC = "cpi.upload";
        public static final String CPI_UPLOAD_STATUS_TOPIC = "cpi.upload.status";
        public static final String CPK_FILE_TOPIC = "cpk.file";
    }
}
