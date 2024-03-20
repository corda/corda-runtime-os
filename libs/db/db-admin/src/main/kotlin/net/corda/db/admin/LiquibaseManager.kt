package net.corda.db.admin

import liquibase.Liquibase
import java.io.Writer

/**
 * Liquibase management helpers
 *
 * @constructor Create Liquibase manager
 */
interface LiquibaseManager {
    /**
     * Update Liquibase database with optional output file and tag
     *
     * @param lb The liquibase object to run the update on
     * @param sql The writer to write the sql to
     * @param tag The tag to apply to the change if any
     */
    fun update(lb: Liquibase, sql: Writer? = null, tag: String? = null)
}