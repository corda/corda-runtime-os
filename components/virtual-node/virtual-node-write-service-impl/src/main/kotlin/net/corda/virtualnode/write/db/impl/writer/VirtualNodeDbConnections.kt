package net.corda.virtualnode.write.db.impl.writer

import java.util.UUID

/**
 * Stores IDs of virtual node DB connections
 */
@Suppress("LongParameterList")
class VirtualNodeDbConnections(
    /** Vault DDL DB connection ID */
    val vaultDdlConnectionId: UUID? = null,
    /** Vault DML DB connection ID */
    val vaultDmlConnectionId: UUID,
    /** Crypto DDL DB connection ID */
    val cryptoDdlConnectionId: UUID? = null,
    /** Crypto DML DB connection ID */
    val cryptoDmlConnectionId: UUID,
    /** Uniqueness DDL DB connection ID */
    val uniquenessDdlConnectionId: UUID? = null,
    /** Uniqueness DML DB connection ID */
    val uniquenessDmlConnectionId: UUID)
