package net.corda.libs.statemanager.impl.repository.impl

import net.corda.libs.statemanager.api.Operation
import net.corda.db.schema.DbSchema.STATE_MANAGER_TABLE

class PostgresQueryProvider : AbstractQueryProvider() {
    override val createState: String
        get() = """
            INSERT INTO $STATE_MANAGER_TABLE
            VALUES (:$KEY_PARAMETER_NAME, :$VALUE_PARAMETER_NAME, :$VERSION_PARAMETER_NAME, CAST(:$METADATA_PARAMETER_NAME as JSONB), CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
        """.trimIndent()

    override val updateState: String
        get() = """
            UPDATE $STATE_MANAGER_TABLE SET
            key = :$KEY_PARAMETER_NAME, value = :$VALUE_PARAMETER_NAME, version = version + 1, metadata = CAST(:$METADATA_PARAMETER_NAME as JSONB), modified_time = CURRENT_TIMESTAMP AT TIME ZONE 'UTC'
            WHERE key = :$KEY_PARAMETER_NAME
        """.trimIndent()

    override fun statesFilteredByMetadataKey(key: String, operation: Operation, value: Any): String {
        return """
            SELECT s.key, s.value, s.metadata, s.version, s.modified_time
            FROM $STATE_MANAGER_TABLE s
            WHERE (${metadataKeyFilter(key, operation, value)})
        """.trimIndent()
    }

    override fun statesUpdatedBetweenAndFilteredByMetadataKey(key: String, operation: Operation, value: Any): String {
        return """
            SELECT s.key, s.value, s.metadata, s.version, s.modified_time
            FROM $STATE_MANAGER_TABLE s
            WHERE (${metadataKeyFilter(key, operation, value)}) AND (${updatedBetweenFilter()})
        """.trimIndent()
    }

    fun metadataKeyFilter(key: String, operation: Operation, value: Any) =
        "(s.metadata->>'$key')::::${value.toNativeType()} ${operation.toNativeOperator()} '$value'"

    private fun Any.toNativeType() = when (this) {
        is String -> "text"
        is Number -> "numeric"
        is Boolean -> "boolean"
        else -> throw IllegalArgumentException("Unsupported Type: ${this::class.java.simpleName}")
    }

    private fun Operation.toNativeOperator() = when (this) {
        Operation.Equals -> "="
        Operation.NotEquals -> "<>"
        Operation.LesserThan -> "<"
        Operation.GreaterThan -> ">"
    }
}
