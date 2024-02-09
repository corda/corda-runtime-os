package net.corda.db.core.utils

import java.sql.Connection
import java.sql.PreparedStatement

/**
 * [BatchPersistenceService] provides batch insert functionality to improve performance.
 */
interface BatchPersistenceService {

    /**
     * Persists a batch of inserts.
     *
     * Uses default values for the number of rows per insert and per batch.
     *
     * The query strings returned by [query] must use `?` as placeholders instead of `:param' as the underlying implementation uses
     * [PreparedStatement]s.
     *
     * @param connection A connection to the database.
     *
     * @param query A function that creates a query. The input to the query is the number of rows to be inserted by a single insert
     * statement. The query string should use this parameter to modify its contents to handle inserting a `batchSize` number of rows.
     * For example:
     *  ```kotlin
     *  INSERT INTO utxo_transaction_sources(transaction_id, group_idx, leaf_idx, source_state_transaction_id, source_state_idx)
     *  VALUES ${List(batchSize) { "(?, ?, ?, ?, ?)" }.joinToString(",")}
     *  ```
     *
     * @param rowData The rows to be inserted.
     *
     * @param setRowParametersBlock A callback that sets the parameters on the provided [PreparedStatement] for a row of data from
     * [rowData]. This callback is executed for every entry in [rowData]. `parameterIndex` should be used to get the index for each
     * parameter passed into statement. For example:
     *  ```kotlin
     *  { statement, parameterIndex, transactionSource ->
     *     statement.setString(parameterIndex.next(), transactionId)
     *     statement.setInt(parameterIndex.next(), transactionSource.group.ordinal)
     *     statement.setInt(parameterIndex.next(), transactionSource.index)
     *     statement.setString(parameterIndex.next(), transactionSource.sourceTransactionId)
     *     statement.setInt(parameterIndex.next(), transactionSource.sourceIndex)
     *  }
     *  ```
     */
    fun <R> persistBatch(
        connection: Connection,
        query: (batchSize: Int) -> String,
        rowData: List<R>,
        setRowParametersBlock: (statement: PreparedStatement, parameterIndex: Iterator<Int>, row: R) -> Unit
    )

    /**
     * Persists a batch of inserts.
     *
     * The query strings returned by [query] must use `?` as placeholders instead of `:param' as the underlying implementation uses
     * [PreparedStatement]s.
     *
     * @param connection A connection to the database.
     *
     * @param query A function that creates a query. The input to the query is the number of rows to be inserted by a single insert
     * statement. The query string should use this parameter to modify its contents to handle inserting a `batchSize` number of rows.
     * For example:
     *  ```kotlin
     *  INSERT INTO utxo_transaction_sources(transaction_id, group_idx, leaf_idx, source_state_transaction_id, source_state_idx)
     *  VALUES ${List(batchSize) { "(?, ?, ?, ?, ?)" }.joinToString(",")}
     *  ```
     *
     * @param rowsPerInsert The number of rows inserted per insert statement.
     *
     * @param insertsPerBatch The number of insert statements per batch of statements.
     *
     * @param rowData The rows to be inserted.
     *
     * @param setRowParametersBlock A callback that sets the parameters on the provided [PreparedStatement] for a row of data from
     * [rowData]. This callback is executed for every entry in [rowData]. `parameterIndex` should be used to get the index for each
     * parameter passed into statement. For example:
     *  ```kotlin
     *  { statement, parameterIndex, transactionSource ->
     *     statement.setString(parameterIndex.next(), transactionId)
     *     statement.setInt(parameterIndex.next(), transactionSource.group.ordinal)
     *     statement.setInt(parameterIndex.next(), transactionSource.index)
     *     statement.setString(parameterIndex.next(), transactionSource.sourceTransactionId)
     *     statement.setInt(parameterIndex.next(), transactionSource.sourceIndex)
     *  }
     *  ```
     */
    @Suppress("LongParameterList")
    fun <R> persistBatch(
        connection: Connection,
        query: (batchSize: Int) -> String,
        rowsPerInsert: Int,
        insertsPerBatch: Int,
        rowData: List<R>,
        setRowParametersBlock: (statement: PreparedStatement, parameterIndex: Iterator<Int>, row: R) -> Unit
    )
}
