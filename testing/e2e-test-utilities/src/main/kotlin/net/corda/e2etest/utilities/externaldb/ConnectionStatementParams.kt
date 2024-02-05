package net.corda.e2etest.utilities.externaldb

import net.corda.db.core.DbPrivilege

class ConnectionStatementParams(
    connectionUsername: String,
    val privilege: DbPrivilege,
    currentSchema: String,
    schemaOwner: String? = null
) {
    val schemaOwner: String = (schemaOwner ?: connectionUsername.also {
        require(privilege == DbPrivilege.DDL) {"DML connections should set schemaOwner"}
    }).lowercase()
    val currentSchema: String = currentSchema.lowercase()
    val connectionUsername: String = connectionUsername.lowercase()
}