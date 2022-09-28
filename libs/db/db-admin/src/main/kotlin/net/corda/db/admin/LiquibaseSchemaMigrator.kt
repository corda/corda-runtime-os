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
    fun updateDb(datasource: Connection, dbChange: DbChange, tag: String? = null)

    /**
     * Update [datasource] using [dbChange] provided.
     *
     * @param datasource
     * @param dbChange
     * @param controlTablesSchema schema for the databasechangelog tables
     */
    fun updateDb(datasource: Connection, dbChange: DbChange, controlTablesSchema: String, tag: String? = null)

    /**
     * Rollback [datasource] using [dbChange] provided.
     *
     * @param datasource
     * @param dbChange
     */
    fun rollBackDb(datasource: Connection, dbChange: DbChange, tagToRollbackTo: String)

    /**
     * Rollback [datasource] using [dbChange] provided.
     *
     * @param datasource
     * @param dbChange
     * @param controlTablesSchema schema for the databasechangelog tables
     */
    fun rollBackDb(datasource: Connection, dbChange: DbChange, controlTablesSchema: String, tagToRollbackTo: String)

    /**
     * Create update [sql] for [datasource] based on [dbChange] but
     * does not update the DB
     *
     * @param datasource
     * @param dbChange
     * @param sql output
     */
    fun createUpdateSql(datasource: Connection, dbChange: DbChange, sql: Writer)

    /**
     * Create update [sql] for [datasource] based on [dbChange] but
     * does not update the DB
     *
     * @param datasource
     * @param dbChange
     * @param controlTablesSchema schema for the databasechangelog tables
     * @param sql output
     */
    fun createUpdateSql(datasource: Connection, dbChange: DbChange, controlTablesSchema: String, sql: Writer)
}

