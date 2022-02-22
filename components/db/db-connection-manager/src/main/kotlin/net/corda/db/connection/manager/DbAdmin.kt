package net.corda.db.connection.manager

import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfigFactory

/**
 * Db admin component to manage creation of "logical" DB (Schemas) and users.
 */
interface DbAdmin {

    // TODO Remove method below

    /**
     * Create "logical" DB (Schema) with [schemaName] for [user] and the given [privilege]
     */
    @Suppress("LongParameterList")
    fun createDbAndUser(
        persistenceUnitName: String,
        schemaName: String,
        user: String,
        password: String,
        jdbcUrl: String,
        privilege: DbPrivilege,
        configFactory: SmartConfigFactory
    )

    /**
     * Create "logical" DB (Schema) with [schemaName] for [user] and the given [privilege]
     */
    @Suppress("LongParameterList")
    fun createDbAndUser(
        schemaName: String,
        user: String,
        password: String,
        jdbcUrl: String,
        privilege: DbPrivilege,
    )

    fun createJdbcUrl(jdbcUrl: String, schemaName: String): String
}