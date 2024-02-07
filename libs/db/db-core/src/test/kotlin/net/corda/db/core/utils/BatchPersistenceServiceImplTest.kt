package net.corda.db.core.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.sql.PreparedStatement

@Suppress("MaxLineLength")
class BatchPersistenceServiceImplTest {

    private val connection = mock<Connection>()
    private val statement = mock<PreparedStatement>()

    private val batchPersistenceService = BatchPersistenceServiceImpl()

    private var rowCallbackCount = 0
    private val batchSizes = mutableListOf<Int>()
    private val query: (Int) -> String = { batchSize ->
        batchSizes += batchSize
        batchSize.toString()
    }

    private data class Row(val index: Int, val value: String)

    private val setRowParametersBlock: (PreparedStatement, Iterator<Int>, Row) -> Unit = { statement, parameterIndex, row ->
        statement.setInt(parameterIndex.next(), row.index)
        statement.setString(parameterIndex.next(), row.value)
        rowCallbackCount++
    }

    @BeforeEach
    fun beforeEach() {
        whenever(connection.prepareStatement(any())).thenReturn(statement)
    }

    @Test
    fun `executes single insert when there are less rows than rowsPerInsert parameter`() {
        batchPersistenceService.persistBatch(
            connection,
            query,
            rowsPerInsert = 5,
            insertsPerBatch = 3,
            rowData = (1..4).map { Row(it, it.toString()) },
            setRowParametersBlock
        )
        verify(statement).executeUpdate()
        verify(statement, never()).addBatch()
        verify(statement, never()).executeBatch()
        assertThat(rowCallbackCount).isEqualTo(4)
        assertThat(batchSizes).containsOnly(4)
    }

    @Test
    fun `executes single insert when there are equal rows as rowsPerInsert parameter`() {
        batchPersistenceService.persistBatch(
            connection,
            query,
            rowsPerInsert = 5,
            insertsPerBatch = 3,
            rowData = (1..5).map { Row(it, it.toString()) },
            setRowParametersBlock
        )
        verify(statement).addBatch()
        verify(statement).executeBatch()
        assertThat(rowCallbackCount).isEqualTo(5)
        assertThat(batchSizes).containsOnly(5)
    }

    @Test
    fun `returns when there are no rows`() {
        batchPersistenceService.persistBatch(
            connection,
            query,
            rowsPerInsert = 5,
            insertsPerBatch = 3,
            rowData = emptyList(),
            setRowParametersBlock
        )
        verifyNoInteractions(statement)
        assertThat(rowCallbackCount).isEqualTo(0)
    }

    @Test
    fun `executes multiple inserts when there are more rows than rowsPerInsert with the last insert having less rows than rowsPerInsert`() {
        batchPersistenceService.persistBatch(
            connection,
            query,
            rowsPerInsert = 5,
            insertsPerBatch = 3,
            rowData = (1..8).map { Row(it, it.toString()) },
            setRowParametersBlock
        )
        verify(statement).executeUpdate()
        verify(statement).addBatch()
        verify(statement).executeBatch()
        assertThat(rowCallbackCount).isEqualTo(8)
        assertThat(batchSizes).containsOnly(5, 3)
    }

    @Test
    fun `executes multiple inserts when there are more rows than rowsPerInsert with the last insert having equals rows as rowsPerInsert`() {
        batchPersistenceService.persistBatch(
            connection,
            query,
            rowsPerInsert = 5,
            insertsPerBatch = 3,
            rowData = (1..10).map { Row(it, it.toString()) },
            setRowParametersBlock
        )
        verify(statement, never()).executeUpdate()
        verify(statement, times(2)).addBatch()
        verify(statement).executeBatch()
        assertThat(rowCallbackCount).isEqualTo(10)
        assertThat(batchSizes).containsOnly(5, 5)
    }

    @Test
    fun `executes single batch of inserts when there are equal rows as insertsPerBatch x rowsPerInsert`() {
        batchPersistenceService.persistBatch(
            connection,
            query,
            rowsPerInsert = 5,
            insertsPerBatch = 3,
            rowData = (1..15).map { Row(it, it.toString()) },
            setRowParametersBlock
        )
        verify(statement, never()).executeUpdate()
        verify(statement, times(3)).addBatch()
        verify(statement).executeBatch()
        assertThat(rowCallbackCount).isEqualTo(15)
        assertThat(batchSizes).containsOnly(5, 5, 5)
    }

    @Test
    fun `executes multiple batches of inserts when the number of rows are a multiple of insertsPerBatch x rowsPerInsert`() {
        batchPersistenceService.persistBatch(
            connection,
            query,
            rowsPerInsert = 5,
            insertsPerBatch = 3,
            rowData = (1..45).map { Row(it, it.toString()) },
            setRowParametersBlock
        )
        verify(statement, never()).executeUpdate()
        verify(statement, times(9)).addBatch()
        verify(statement, times(3)).executeBatch()
        assertThat(rowCallbackCount).isEqualTo(45)
        assertThat(batchSizes).containsOnly(5, 5, 5, 5, 5, 5, 5, 5, 5)
    }

    @Test
    fun `executes multiple batches of inserts and a single insert when there are more rows than insertsPerBatch x rowsPerInsert`() {
        batchPersistenceService.persistBatch(
            connection,
            query,
            rowsPerInsert = 5,
            insertsPerBatch = 3,
            rowData = (1..47).map { Row(it, it.toString()) },
            setRowParametersBlock
        )
        verify(statement).executeUpdate()
        verify(statement, times(9)).addBatch()
        verify(statement, times(3)).executeBatch()
        assertThat(rowCallbackCount).isEqualTo(47)
        assertThat(batchSizes).containsOnly(5, 5, 5, 5, 5, 5, 5, 5, 5, 2)
    }

    @Test
    fun `parameterIndex updates the index correctly within a single insert statement`() {
        val rows = (1..5).map { Row(it, it.toString()) }
        batchPersistenceService.persistBatch(
            connection,
            query,
            rowsPerInsert = 5,
            insertsPerBatch = 3,
            rowData = rows,
            setRowParametersBlock
        )
        inOrder(statement) {
            rows.forEachIndexed { rowIndex, (index, value) ->
                verify(statement).setInt((rowIndex * 2) + 1, index)
                verify(statement).setString((rowIndex * 2) + 2, value)
            }
        }
    }

    @Test
    fun `parameterIndex updates the index correctly within a single insert statement with less rows than rowsPerInsert`() {
        val rows = (1..4).map { Row(it, it.toString()) }
        batchPersistenceService.persistBatch(
            connection,
            query,
            rowsPerInsert = 5,
            insertsPerBatch = 3,
            rowData = rows,
            setRowParametersBlock
        )
        inOrder(statement) {
            rows.forEachIndexed { rowIndex, (index, value) ->
                verify(statement).setInt((rowIndex * 2) + 1, index)
                verify(statement).setString((rowIndex * 2) + 2, value)
            }
        }
    }

    @Test
    fun `parameterIndex updates the index correctly within a single batch of inserts when the last insert has less rows than rowsPerInsert`() {
        val rows = (1..8).map { Row(it, it.toString()) }
        batchPersistenceService.persistBatch(
            connection,
            query,
            rowsPerInsert = 5,
            insertsPerBatch = 3,
            rowData = rows,
            setRowParametersBlock
        )
        inOrder(statement) {
            rows.take(5).forEachIndexed { rowIndex, (index, value) ->
                verify(statement).setInt((rowIndex * 2) + 1, index)
                verify(statement).setString((rowIndex * 2) + 2, value)
            }
            rows.takeLast(3).forEachIndexed { rowIndex, (index, value) ->
                verify(statement).setInt((rowIndex * 2) + 1, index)
                verify(statement).setString((rowIndex * 2) + 2, value)
            }
        }
    }

    @Test
    fun `parameterIndex updates the index correctly across batches of inserts`() {
        val rows = (1..16).map { Row(it, it.toString()) }
        batchPersistenceService.persistBatch(
            connection,
            query,
            rowsPerInsert = 5,
            insertsPerBatch = 3,
            rowData = rows,
            setRowParametersBlock
        )
        inOrder(statement) {
            rows.chunked(5).map { chunked ->
                chunked.forEachIndexed { rowIndex, (index, value) ->
                    verify(statement).setInt((rowIndex * 2) + 1, index)
                    verify(statement).setString((rowIndex * 2) + 2, value)
                }
            }
        }
    }
}
