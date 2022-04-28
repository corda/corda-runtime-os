package net.corda.db.schema

/**
 * Explicitly lists DB schema names to be used for applying Liquibase scripts.
 * Since Liquibase does not allow specifying schema as part of change set definition.
 * For more information, please see [here](https://docs.liquibase.com/concepts/advanced/liquibase-schema-name-parameter.html).
 */
object DbSchema {
    const val RPC_RBAC = "RPC_RBAC"

    const val CONFIG = "CONFIG"
    const val CONFIG_TABLE = "config"
    const val CONFIG_AUDIT_TABLE = "config_audit"
    const val CONFIG_AUDIT_ID_SEQUENCE = "config_audit_id_seq"
    const val CONFIG_AUDIT_ID_SEQUENCE_ALLOC_SIZE = 1
    
    const val DB_CONNECTION_TABLE = "db_connection"
    const val DB_CONNECTION_AUDIT_TABLE = "db_connection_audit"
    const val DB_CONNECTION_AUDIT_ID_SEQUENCE = "db_connection_audit_id_seq"
    const val DB_CONNECTION_AUDIT_ID_SEQUENCE_ALLOC_SIZE = 1

    const val VNODE = "VNODE"
    const val VNODE_INSTANCE_DB_TABLE = "vnode_instance"
    const val HOLDING_IDENTITY_DB_TABLE = "holding_identity"
    const val VNODE_VAULT_DB_TABLE = "vnode_vault"
    const val VNODE_KEY_DB_TABLE = "vnode_key"
    const val VNODE_CERTIFICATE_DB_TABLE = "vnode_certificate"

    const val DB_MESSAGE_BUS = "DB_MESSAGE_BUS"

    // The values here are placeholders, until reasonable values are determined.
    const val CPI_REVISION_SEQUENCE = "r_db"
    const val CPI_REVISION_SEQUENCE_ALLOC_SIZE = 1

    const val CRYPTO = "CRYPTO"
    const val CRYPTO_WRAPPING_KEY_TABLE = "crypto_wrapping_key"
    const val CRYPTO_SIGNING_KEY_TABLE = "crypto_signing_key"
}
