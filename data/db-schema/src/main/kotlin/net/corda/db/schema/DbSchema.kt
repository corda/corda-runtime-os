package net.corda.db.schema

/**
 * Explicitly lists DB schema names to be used for applying Liquibase scripts.
 * Since Liquibase does not allow specifying schema as part of change set definition.
 * For more information, please see [here](https://docs.liquibase.com/concepts/advanced/liquibase-schema-name-parameter.html).
 */
object DbSchema {
    const val RBAC = "RBAC"

    const val CONFIG = "CONFIG"
    const val CONFIG_TABLE = "config"
    const val CONFIG_AUDIT_TABLE = "config_audit"
    const val CONFIG_AUDIT_ID_SEQUENCE = "config_audit_id_seq"
    const val CLUSTER_CERTIFICATE_DB_TABLE = "cluster_certificate"
    const val CONFIG_AUDIT_ID_SEQUENCE_ALLOC_SIZE = 1

    const val DB_CONNECTION_TABLE = "db_connection"
    const val DB_CONNECTION_AUDIT_TABLE = "db_connection_audit"
    const val DB_CONNECTION_AUDIT_ID_SEQUENCE = "db_connection_audit_id_seq"
    const val DB_CONNECTION_AUDIT_ID_SEQUENCE_ALLOC_SIZE = 1

    const val VNODE = "VNODE"
    const val VIRTUAL_NODE_DB_TABLE = "virtual_node"
    const val HOLDING_IDENTITY_DB_TABLE = "holding_identity"
    const val VNODE_VAULT_DB_TABLE = "vnode_vault"
    const val VNODE_KEY_DB_TABLE = "vnode_key"
    const val VNODE_CERTIFICATE_DB_TABLE = "vnode_certificate"
    const val VNODE_ALLOWED_CERTIFICATE_DB_TABLE = "vnode_mtls_allowed_certificate"
    const val VNODE_GROUP_REGISTRATION_TABLE = "vnode_registration_request"
    const val VNODE_MEMBER_INFO = "vnode_member_info"
    const val VNODE_GROUP_POLICY = "vnode_group_policy"
    const val VNODE_MEMBER_SIGNATURE = "vnode_member_signature"
    const val VNODE_GROUP_PARAMETERS = "vnode_group_parameters"
    const val VNODE_GROUP_APPROVAL_RULES = "vnode_group_approval_rules"

    const val LEDGER_CONSENSUAL_TRANSACTION_TABLE = "consensual_transaction"
    const val LEDGER_CONSENSUAL_TRANSACTION_STATUS_TABLE = "consensual_transaction_status"
    const val LEDGER_CONSENSUAL_TRANSACTION_SIGNATURE_TABLE = "consensual_transaction_signature"
    const val LEDGER_CONSENSUAL_TRANSACTION_COMPONENT_TABLE = "consensual_transaction_component"
    const val LEDGER_CONSENSUAL_CPK_TABLE = "consensual_cpk"
    const val LEDGER_CONSENSUAL_TRANSACTION_CPK_TABLE = "consensual_transaction_cpk"

    const val DB_MESSAGE_BUS = "DB_MESSAGE_BUS"

    const val CRYPTO = "CRYPTO"
    const val CRYPTO_WRAPPING_KEY_TABLE = "crypto_wrapping_key"
    const val CRYPTO_SIGNING_KEY_TABLE = "crypto_signing_key"
    const val CRYPTO_HSM_CONFIG_TABLE = "crypto_hsm_config"
    const val CRYPTO_HSM_CATEGORY_MAP_TABLE = "crypto_hsm_category_map"
    const val CRYPTO_HSM_ASSOCIATION_TABLE = "crypto_hsm_association"
    const val CRYPTO_HSM_CATEGORY_ASSOCIATION_TABLE = "crypto_hsm_category_association"

    const val UNIQUENESS = "UNIQUENESS"
    const val UNIQUENESS_STATE_DETAILS_TABLE = "uniqueness_state_details"
    const val UNIQUENESS_TX_DETAILS_TABLE = "uniqueness_tx_details"
    const val UNIQUENESS_REJECTED_TX_TABLE = "uniqueness_rejected_txs"
}
