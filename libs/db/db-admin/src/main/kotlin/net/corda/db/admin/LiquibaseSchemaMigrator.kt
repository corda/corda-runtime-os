package net.corda.db.admin

import java.io.Writer
import java.sql.Connection

/**
 * Liquibase schema migrator
 *
 * @constructor Create Liquibase schema migrator
 */
interface LiquibaseSchemaMigrator {

    companion object {
        const val PUBLIC_SCHEMA = "PUBLIC"
    }

    /**
     * Update [datasource] using [dbChange] provided.
     *
     * @param datasource
     * @param dbChange
     * @param liquibaseSchemaName schema to be used for Liquibase changelog
     */
    fun updateDb(datasource: Connection, dbChange: DbChange, liquibaseSchemaName: String)

    /**
     * Create update [sql] for [datasource] based on [dbChange] but
     * does not update the DB
     *
     * @param datasource
     * @param dbChange
     * @param sql output
     * @param liquibaseSchemaName schema to be used for Liquibase changelog
     */
    fun createUpdateSql(datasource: Connection, dbChange: DbChange, sql: Writer, liquibaseSchemaName: String)
}

