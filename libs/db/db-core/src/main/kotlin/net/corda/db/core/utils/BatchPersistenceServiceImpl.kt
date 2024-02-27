package net.corda.db.core.utils

import java.sql.Connection
import java.sql.PreparedStatement

class BatchPersistenceServiceImpl : BatchPersistenceService {

    private companion object {
        const val ROWS_PER_INSERT = 30
        const val INSERTS_PER_BATCH = 10
    }

    override fun <R> persistBatch(
        connection: Connection,
        query: (Int) -> String,
        rowData: List<R>,
        setRowParametersBlock: (statement: PreparedStatement, parameterIndex: Iterator<Int>, row: R) -> Unit
    ) {
        persistBatch(connection, query, ROWS_PER_INSERT, INSERTS_PER_BATCH, rowData, setRowParametersBlock)
    }

    override fun <R> persistBatch(
        connection: Connection,
        query: (Int) -> String,
        rowsPerInsert: Int,
        insertsPerBatch: Int,
        rowData: List<R>,
        setRowParametersBlock: (statement: PreparedStatement, parameterIndex: Iterator<Int>, row: R) -> Unit
    ) {
        if (rowData.isEmpty()) return

        val batched = rowData.chunked(insertsPerBatch * rowsPerInsert)
        batched.forEachIndexed { index, batch ->
            val batchPerInsert = batch.chunked(rowsPerInsert)
            val hasReducedRowsOnLastInsert = index == batched.lastIndex && batchPerInsert.last().size < rowsPerInsert

            if (!hasReducedRowsOnLastInsert || batchPerInsert.size > 1) {
                connection.prepareStatement(query(rowsPerInsert)).use { statement ->
                    batchPerInsert.forEachIndexed perInsertLoop@{ index, rowsPerInsert ->
                        if (hasReducedRowsOnLastInsert && index == batchPerInsert.lastIndex) {
                            return@perInsertLoop
                        }
                        val parameterIndex = generateSequence(1) { it + 1 }.iterator()
                        rowsPerInsert.forEach { row ->
                            setRowParametersBlock(statement, parameterIndex, row)
                        }
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
            }
            if (hasReducedRowsOnLastInsert) {
                connection.prepareStatement(query(batchPerInsert.last().size)).use { statement ->
                    val parameterIndex = generateSequence(1) { it + 1 }.iterator()
                    batchPerInsert.last().forEach { row ->
                        setRowParametersBlock(statement, parameterIndex, row)
                    }
                    statement.executeUpdate()
                }
            }
        }
    }
}
