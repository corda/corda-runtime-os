package net.corda.db.schema

/**
 * Explicitly lists DB schema names to be used for applying Liquibase scripts.
 * Since Liquibase does not allow specifying schema as part of change set definition.
 * For more information, please see [here](https://docs.liquibase.com/concepts/advanced/liquibase-schema-name-parameter.html).
 */
object DbSchema {
    const val RPC_RBAC = "RPC_RBAC"

    const val CONFIG = "CONFIG"
    const val CONFIG_DB_TABLE = "config"
    const val CONFIG_AUDIT_DB_TABLE = "config_audit"
    const val CONFIG_AUDIT_ID_SEQUENCE = "config_audit_id_seq"
    const val CONFIG_AUDIT_ID_SEQUENCE_ALLOC_SIZE = 1
    const val CONFIG_DB_CONNECTION_TABLE = "db_connection"

    const val DB_MESSAGE_BUS = "DB_MESSAGE_BUS"
}

