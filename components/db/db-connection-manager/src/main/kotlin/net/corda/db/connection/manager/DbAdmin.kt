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
        privilege: DbPrivilege,
    )

    /**
     * Check whether user exists in DB
     *
     * @param user Username
     * @return true if user exists in Db, false otherwise
     */
    fun userExists(user: String): Boolean

    /**
     * Delete DB schema and user
     *
     * @param schemaName Schema name
     * @param user Username
     */
    fun deleteSchemaAndUser(schemaName: String, user: String)

    /**
     * Configure JDBC URL to use given DB schema
     *
     * @param jdbcUrl JDBC URL
     * @param schemaName Schema name
     * @return JDBC URL configured to use given DB schema
     */
    fun createJdbcUrl(jdbcUrl: String, schemaName: String): String
}