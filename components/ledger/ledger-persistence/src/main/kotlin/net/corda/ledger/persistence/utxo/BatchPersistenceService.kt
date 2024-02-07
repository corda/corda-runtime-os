package net.corda.ledger.persistence.utxo

import java.sql.PreparedStatement
import javax.persistence.EntityManager

interface BatchPersistenceService {

    fun <R> persistBatch(
        entityManager: EntityManager,
        query: (Int) -> String,
        rowData: List<R>,
        setRowParametersBlock: (statement: PreparedStatement, parameterIndex: Iterator<Int>, row: R) -> Unit
    )

    @Suppress("LongParameterList")
    fun <R> persistBatch(
        entityManager: EntityManager,
        query: (Int) -> String,
        rowsPerInsert: Int,
        insertsPerBatch: Int,
        rowData: List<R>,
        setRowParametersBlock: (statement: PreparedStatement, parameterIndex: Iterator<Int>, row: R) -> Unit
    )
}
