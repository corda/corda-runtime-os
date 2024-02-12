package net.corda.libs.statemanager.impl.repository.impl

import net.corda.db.schema.DbSchema.STATE_MANAGER_TABLE
import net.corda.libs.statemanager.impl.model.v1.StateColumns.KEY_COLUMN
import net.corda.libs.statemanager.impl.model.v1.StateColumns.MODIFIED_TIME_COLUMN

abstract class AbstractQueryProvider : QueryProvider {

    override fun findStatesByKey(size: Int): String {
        val placeholders = List(size) { "?" }.joinToString(",")
        return """
            SELECT s.*, m.key AS metadata_key, m.value AS metadata_value
            FROM state s
            LEFT JOIN statemeta m ON s.key = m.statekey
            WHERE s.key IN ($placeholders)
        """.trimIndent()
    }

    override val deleteStatesByKey: String
        get() = """
            DELETE FROM $STATE_MANAGER_TABLE s WHERE s.$KEY_COLUMN = ?
        """.trimIndent()

    override val deleteMetaStatesByKey: String
        get() = """
            DELETE FROM statemeta m WHERE m.statekey = ?
        """.trimIndent()

    override val findStatesUpdatedBetween: String
        get() = """            
            SELECT s.*, m.key AS metadata_key, m.value AS metadata_value
            FROM $STATE_MANAGER_TABLE s
            LEFT JOIN statemeta m ON s.key = m.statekey
            WHERE ${updatedBetweenFilter()}
        """.trimIndent()

    fun updatedBetweenFilter() = "s.$MODIFIED_TIME_COLUMN BETWEEN ? AND ?"
}
