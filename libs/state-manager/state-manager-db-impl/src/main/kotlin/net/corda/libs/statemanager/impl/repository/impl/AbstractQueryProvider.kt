package net.corda.libs.statemanager.impl.repository.impl

import net.corda.db.schema.DbSchema.STATE_MANAGER_TABLE

const val KEY_PARAMETER_NAME = "key"
const val KEYS_PARAMETER_NAME = "keys"
const val VALUE_PARAMETER_NAME = "value"
const val VERSION_PARAMETER_NAME = "version"
const val METADATA_PARAMETER_NAME = "metadata"
const val START_TIMESTAMP_PARAMETER_NAME = "startTime"
const val FINISH_TIMESTAMP_PARAMETER_NAME = "finishTime"

abstract class AbstractQueryProvider : QueryProvider {

    override val findStatesByKey: String
        get() = """
            SELECT s.key, s.value, s.metadata, s.version, s.modified_time FROM $STATE_MANAGER_TABLE s
            WHERE s.key IN (:$KEYS_PARAMETER_NAME)
        """.trimIndent()

    override val deleteStatesByKey: String
        get() = """
            DELETE FROM $STATE_MANAGER_TABLE s WHERE s.key = :$KEY_PARAMETER_NAME AND s.version = :$VERSION_PARAMETER_NAME
        """.trimIndent()

    override val findStatesUpdatedBetween: String
        get() = """
            SELECT s.key, s.value, s.metadata, s.version, s.modified_time FROM $STATE_MANAGER_TABLE s
            WHERE ${updatedBetweenFilter()}
        """.trimIndent()

    fun updatedBetweenFilter() =
        "s.modified_time BETWEEN :$START_TIMESTAMP_PARAMETER_NAME AND :$FINISH_TIMESTAMP_PARAMETER_NAME"
}
