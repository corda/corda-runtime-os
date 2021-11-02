package net.corda.db.schema

/**
 * Explicitly lists DB schema names to be used for applying Liquibase scripts.
 * Since Liquibase does not allow specifying schema as part of change set definition.
 * For more information, please see [here](https://docs.liquibase.com/concepts/advanced/liquibase-schema-name-parameter.html).
 */
enum class Schema(val schemaName: String)  {
    RPC_RBAC("RPC_RBAC")
}