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
     * Create "logical" DB (Schema) with [schemaName] for [user] and the given [privilege]. In case of DML privilege,
     * DDL user should be provided as [grantee].
     */
    @Suppress("LongParameterList")
    fun createDbAndUser(
        schemaName: String,
        user: String,
        password: String,
        privilege: DbPrivilege,
        grantee: String? = null
    )

    /**
     * Delete DB schema
     *
     * @param schemaName Schema name
     */
    fun deleteSchema(schemaName: String)

    /**
     * Check whether user exists in DB
     *
     * @param user Username
     * @return true if user exists in Db, false otherwise
     */
    fun userExists(user: String): Boolean

    /**
     * Delete DB user
     *
     * @param user Username
     */
    fun deleteUser(user: String)

    /**
     * Configure JDBC URL to use given DB schema
     *
     * @param jdbcUrl JDBC URL
     * @param schemaName Schema name
     * @return JDBC URL configured to use given DB schema
     */
    fun createJdbcUrl(jdbcUrl: String, schemaName: String): String
}