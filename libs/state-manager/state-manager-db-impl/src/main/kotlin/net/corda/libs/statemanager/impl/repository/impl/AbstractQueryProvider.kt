package net.corda.libs.statemanager.impl.repository.impl

import net.corda.db.schema.DbSchema.STATE_MANAGER_TABLE
import net.corda.libs.statemanager.impl.model.v1.StateColumns.KEY_COLUMN
import net.corda.libs.statemanager.impl.model.v1.StateColumns.METADATA_COLUMN
import net.corda.libs.statemanager.impl.model.v1.StateColumns.MODIFIED_TIME_COLUMN
import net.corda.libs.statemanager.impl.model.v1.StateColumns.VALUE_COLUMN
import net.corda.libs.statemanager.impl.model.v1.StateColumns.VERSION_COLUMN
import java.util.TimeZone

abstract class AbstractQueryProvider : QueryProvider {
    override val timeZone: TimeZone
        get() = TimeZone.getTimeZone("UTC")

    override fun findStatesByKey(size: Int) =
        """
            SELECT s.$KEY_COLUMN, s.$VALUE_COLUMN, s.$METADATA_COLUMN, s.$VERSION_COLUMN, s.modified_time FROM $STATE_MANAGER_TABLE s
            WHERE s.$KEY_COLUMN IN (${List(size) { "?" }.joinToString(",")} )
        """.trimIndent()

    override val deleteStatesByKey: String
        get() = """
            DELETE FROM $STATE_MANAGER_TABLE s WHERE s.$KEY_COLUMN = ? AND s.$VERSION_COLUMN = ?
        """.trimIndent()

    override val findStatesUpdatedBetween: String
        get() = """
            SELECT s.$KEY_COLUMN, s.$VALUE_COLUMN, s.$METADATA_COLUMN, s.$VERSION_COLUMN, s.$MODIFIED_TIME_COLUMN FROM $STATE_MANAGER_TABLE s
            WHERE ${updatedBetweenFilter()}
        """.trimIndent()

    fun updatedBetweenFilter() = "s.$MODIFIED_TIME_COLUMN BETWEEN ? AND ?"
}
