package net.corda.db.admin

import liquibase.Liquibase
import java.io.Writer

/**
 * Liquibase management helpers
 */
interface LiquibaseManager {
    /**
     * Update Liquibase database with optional output file and tag
     *
     * @param lb The liquibase object to run the update on.
     * @param sql An optional writer to output the sql into. Note: that if non-null value is passed the Liquibase transform is not actually performed, but intended DB changes are sent to the writer.
     * @param tag An optional tag to apply to the change.
     */
    fun update(lb: Liquibase, sql: Writer? = null, tag: String? = null)
}