package net.corda.libs.statemanager.impl.repository.impl

import org.slf4j.LoggerFactory
import java.sql.Statement

object PreparedStatementHelper {
    private const val NO_RECORD_UPDATED = 0

    private val log = LoggerFactory.getLogger(this::class.java.name)

    data class CommandResultPair(
        val commandKey: String,
        val result: Int
    )

    /**
     * Extracts the list of command keys from commands in a `PreparedStatement` batch that either failed or affected
     * zero rows (did not update, create or delete any rows when it was expected to).
     *
     * The [batchResults] `IntArray` contains the ordered results corresponding to the order of commands in the
     * batch. These values may be:
     *
     * 1. The number of rows updated, or `0` of no rows were updated by a command in the batch.
     * 2. `-2` (SUCCESS_NO_INFO) if a statement executed successfully but no count was returned.
     * 3. `-3` (EXECUTE_FAILED) if a statement failed and the JDBC driver continues to process the rest of the
     *   commands in the batch.
     *
     * If a SQL command declares `ON CONFLICT DO NOTHING` then 0 will be returned in the results array if there
     * is a conflict such as a primary key already existing (if a persist operation).
     *
     * If a SQL command includes an optimistic locking version check in the query then 0 will be returned on a
     * optimistic version lock mismatch.
     *
     * @param batchResults results from `PreparedStatement.executeBatch()`
     * @param commandKeys list of keys for each command, in the order they were added to `PreparedStatement`
     * @return a list of commands IDs that had failures or affected zero rows
     * @throws PreparedStatementHelperException if number of results does not match number of commands
     */
    fun extractFailedKeysFromBatchResults(batchResults: IntArray, commandKeys: List<String>): List<String> {
        if (batchResults.size != commandKeys.size) {
            ("Number of results from batch (size: ${batchResults.size}) " +
                    "does not match number of commands in the request (size ${commandKeys.size}").let {
                log.warn(it)
                throw PreparedStatementHelperException(it)
            }
        }
        return batchResults.zip(commandKeys) { result, key -> CommandResultPair(key, result) }
            .filter { it.result == NO_RECORD_UPDATED || it.result == Statement.EXECUTE_FAILED }
            .map { it.commandKey }
    }
}