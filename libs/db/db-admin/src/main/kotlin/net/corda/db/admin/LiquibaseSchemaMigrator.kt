package net.corda.db.admin

import java.io.Writer
import java.sql.Connection

/**
 * Liquibase schema migrator
 *
 * @constructor Create Liquibase schema migrator
 */
interface LiquibaseSchemaMigrator {
    /**
     * Update [datasource] using [dbChange] provided.
     *
     * @param datasource
     * @param dbChange
     */
    fun updateDb(datasource: Connection, dbChange: DbChange, tagAsLast: Boolean = false)

    /**
     * Update [datasource] using [dbChange] provided.
     *
     * @param datasource
     * @param dbChange
     * @param controlTablesSchema schema for the databasechangelog tables
     */
    fun updateDb(datasource: Connection, dbChange: DbChange, controlTablesSchema: String, tagAsLast: Boolean = false)

    /**
     * Rollback [datasource] using [dbChange] provided.
     *
     * @param datasource
     * @param dbChange
     */
    fun rollbackDb(datasource: Connection, dbChange: DbChange, tag: String?)

    /**
     * Rollback [datasource] using [dbChange] provided.
     *
     * @param datasource
     * @param dbChange
     * @param controlTablesSchema schema for the databasechangelog tables
     */
    fun rollbackDb(datasource: Connection, controlTablesSchema: String, dbChange: DbChange, tag: String?)

    /**
     * Create update [sql] for [datasource] based on [dbChange] but
     * does not update the DB
     *
     * @param datasource
     * @param dbChange
     * @param sql output
     */
    fun createUpdateSql(datasource: Connection, dbChange: DbChange, sql: Writer, tagAsLast: Boolean = false)

    /**
     * Create update [sql] for [datasource] based on [dbChange] but
     * does not update the DB
     *
     * @param datasource
     * @param dbChange
     * @param controlTablesSchema schema for the databasechangelog tables
     * @param sql output
     */
    fun createUpdateSql(datasource: Connection, dbChange: DbChange, controlTablesSchema: String, sql: Writer, tagAsLast: Boolean = false)
}

