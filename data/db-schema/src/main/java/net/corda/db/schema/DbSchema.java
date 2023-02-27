package net.corda.db.schema;

/**
 * Explicitly lists DB schema names to be used for applying Liquibase scripts.
 * Since Liquibase does not allow specifying schema as part of change set definition.
 * For more information, please see <a href="https://docs.liquibase.com/concepts/advanced/liquibase-schema-name-parameter.html">here</a>.
 */
public final class DbSchema {
    private DbSchema() {
    }

    public static final String RBAC = "RBAC";

    public static final String CONFIG = "CONFIG";
    public static final String CONFIG_TABLE = "config";
    public static final String CONFIG_AUDIT_TABLE = "config_audit";
    public static final String CONFIG_AUDIT_ID_SEQUENCE = "config_audit_id_seq";
    public static final String CLUSTER_CERTIFICATE_DB_TABLE = "cluster_certificate";
    public static final int CONFIG_AUDIT_ID_SEQUENCE_ALLOC_SIZE = 1;

    public static final String DB_CONNECTION_TABLE = "db_connection";
    public static final String DB_CONNECTION_AUDIT_TABLE = "db_connection_audit";
    public static final String DB_CONNECTION_AUDIT_ID_SEQUENCE = "db_connection_audit_id_seq";
    public static final int DB_CONNECTION_AUDIT_ID_SEQUENCE_ALLOC_SIZE = 1;

    public static final String VNODE = "VNODE";
    public static final String VIRTUAL_NODE_DB_TABLE = "virtual_node";
    public static final String HOLDING_IDENTITY_DB_TABLE = "holding_identity";
    public static final String VNODE_VAULT_DB_TABLE = "vnode_vault";
    public static final String VNODE_KEY_DB_TABLE = "vnode_key";
    public static final String VNODE_CERTIFICATE_DB_TABLE = "vnode_certificate";
    public static final String VNODE_ALLOWED_CERTIFICATE_DB_TABLE = "vnode_mtls_allowed_certificate";
    public static final String VNODE_GROUP_REGISTRATION_TABLE = "vnode_registration_request";
    public static final String VNODE_MEMBER_INFO = "vnode_member_info";
    public static final String VNODE_GROUP_POLICY = "vnode_group_policy";
    public static final String VNODE_MEMBER_SIGNATURE = "vnode_member_signature";
    public static final String VNODE_GROUP_PARAMETERS = "vnode_group_parameters";
    public static final String VNODE_GROUP_APPROVAL_RULES = "vnode_group_approval_rules";
    public static final String VNODE_PRE_AUTH_TOKENS = "vnode_pre_auth_tokens";

    public static final String LEDGER_CONSENSUAL_TRANSACTION_TABLE = "consensual_transaction";
    public static final String LEDGER_CONSENSUAL_TRANSACTION_STATUS_TABLE = "consensual_transaction_status";
    public static final String LEDGER_CONSENSUAL_TRANSACTION_SIGNATURE_TABLE = "consensual_transaction_signature";
    public static final String LEDGER_CONSENSUAL_TRANSACTION_COMPONENT_TABLE = "consensual_transaction_component";
    public static final String LEDGER_CONSENSUAL_CPK_TABLE = "consensual_cpk";
    public static final String LEDGER_CONSENSUAL_TRANSACTION_CPK_TABLE = "consensual_transaction_cpk";

    public static final String DB_MESSAGE_BUS = "DB_MESSAGE_BUS";

    public static final String CRYPTO = "CRYPTO";
    public static final String CRYPTO_WRAPPING_KEY_TABLE = "crypto_wrapping_key";
    public static final String CRYPTO_SIGNING_KEY_TABLE = "crypto_signing_key";
    public static final String CRYPTO_HSM_CONFIG_TABLE = "crypto_hsm_config";
    public static final String CRYPTO_HSM_CATEGORY_MAP_TABLE = "crypto_hsm_category_map";
    public static final String CRYPTO_HSM_ASSOCIATION_TABLE = "crypto_hsm_association";
    public static final String CRYPTO_HSM_CATEGORY_ASSOCIATION_TABLE = "crypto_hsm_category_association";

    public static final String UNIQUENESS = "UNIQUENESS";
    public static final String UNIQUENESS_STATE_DETAILS_TABLE = "uniqueness_state_details";
    public static final String UNIQUENESS_TX_DETAILS_TABLE = "uniqueness_tx_details";
    public static final String UNIQUENESS_REJECTED_TX_TABLE = "uniqueness_rejected_txs";
}
