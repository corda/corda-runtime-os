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
     * @param tag a mark on the current database state to support rolling back of changes
     */
    fun updateDb(datasource: Connection, dbChange: DbChange, tag: String? = null)

    /**
     * Update [datasource] using [dbChange] provided.
     *
     * @param datasource
     * @param dbChange
     * @param controlTablesSchema schema for the databasechangelog tables
     * @param tag a mark on the current database state to support rolling back of changes
     */
    fun updateDb(datasource: Connection, dbChange: DbChange, controlTablesSchema: String, tag: String? = null)

    /**
     * Rollback [datasource] using [dbChange] provided.
     *
     * @param datasource
     * @param dbChange
     * @param tagToRollbackTo
     */
    fun rollBackDb(datasource: Connection, dbChange: DbChange, tagToRollbackTo: String)

    /**
     * Rollback [datasource] using [dbChange] provided.
     *
     * @param datasource
     * @param dbChange
     * @param controlTablesSchema schema for the databasechangelog tables
     * @param tagToRollbackTo
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

    /**
     * Given the list of dbChange changesets, return a list of these changesets that are not applied in the given datasource.
     *
     * @param datasource the connection of the datasource to compare
     * @param dbChange the changesets to compare
     *
     * @return a list of filepaths for the unrun change sets
     */
    fun listUnrunChangeSets(datasource: Connection, dbChange: DbChange): List<String>
}

