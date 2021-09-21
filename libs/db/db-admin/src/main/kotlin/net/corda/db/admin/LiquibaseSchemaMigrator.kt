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
     * Update [datasource] using [changeLog] file.
     *
     * @param datasource
     * @param dbChange
     */
    fun updateDb(datasource: Connection, dbChange: DbChange)

    /**
     * Create update [sql] for [datasource] based on [changeLog]
     * does not update the DB
     *
     * @param datasource
     * @param dbChange
     * @param sql output
     */
    fun createUpdateSql(datasource: Connection, dbChange: DbChange, sql: Writer)
}

