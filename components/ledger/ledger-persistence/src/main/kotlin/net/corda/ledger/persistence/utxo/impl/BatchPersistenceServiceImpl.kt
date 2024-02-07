package net.corda.ledger.persistence.utxo.impl

import net.corda.ledger.persistence.utxo.BatchPersistenceService
import net.corda.sandbox.type.SandboxConstants
import net.corda.sandbox.type.UsedByPersistence
import org.hibernate.Session
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope
import java.sql.Connection
import java.sql.PreparedStatement
import javax.persistence.EntityManager

@Component(
    service = [BatchPersistenceService::class, UsedByPersistence::class],
    property = [SandboxConstants.CORDA_MARKER_ONLY_SERVICE],
    scope = ServiceScope.PROTOTYPE
)
class BatchPersistenceServiceImpl : BatchPersistenceService, UsedByPersistence {

    private companion object {
        const val ROWS_PER_INSERT = 30
        const val INSERTS_PER_BATCH = 10
    }

    override fun <R> persistBatch(
        entityManager: EntityManager,
        query: (Int) -> String,
        rowData: List<R>,
        setRowParametersBlock: (statement: PreparedStatement, parameterIndex: Iterator<Int>, row: R) -> Unit
    ) {
        persistBatch(entityManager, query, ROWS_PER_INSERT, INSERTS_PER_BATCH, rowData, setRowParametersBlock)
    }

    override fun <R> persistBatch(
        entityManager: EntityManager,
        query: (Int) -> String,
        rowsPerInsert: Int,
        insertsPerBatch: Int,
        rowData: List<R>,
        setRowParametersBlock: (statement: PreparedStatement, parameterIndex: Iterator<Int>, row: R) -> Unit
    ) {
        if (rowData.isEmpty()) return

        entityManager.connection { connection ->
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

    private fun <T> EntityManager.connection(block: (connection: Connection) -> T) {
        val hibernateSession = unwrap(Session::class.java)
        hibernateSession.doWork { connection ->
            block(connection)
        }
    }
}
