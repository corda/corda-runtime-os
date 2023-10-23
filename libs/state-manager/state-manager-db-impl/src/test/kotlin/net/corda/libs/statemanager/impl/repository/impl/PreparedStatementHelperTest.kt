package net.corda.libs.statemanager.impl.repository.impl

import java.sql.Statement
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PreparedStatementHelperTest {

    @Test
    fun `can extract single failure from result set`() {
        val results = intArrayOf(1, 0, 1, 1, 1)
        val keys = listOf("a", "b", "c", "d", "e")

        val extracted = PreparedStatementHelper.extractFailedKeysFromBatchResults(results, keys)

        assertThat(extracted).hasSize(1)
        assertThat(extracted.first()).isEqualTo("b")
    }

    @Test
    fun `can extract multiple failures from result set`() {
        val results = intArrayOf(1, 0, 1, 1, 1, 0, 0, 0, 0)
        val keys = listOf("a", "b", "c", "d", "e", "f", "g", "h", "i")

        val extracted = PreparedStatementHelper.extractFailedKeysFromBatchResults(results, keys)

        assertThat(extracted).hasSize(5)
        assertThat(extracted).isEqualTo(listOf("b", "f", "g", "h", "i"))
    }

    @Test
    fun `can extract success_no_info code from result set`() {
        val results = intArrayOf(1, Statement.SUCCESS_NO_INFO, 1, 1, 1)
        val keys = listOf("a", "b", "c", "d", "e")

        val extracted = PreparedStatementHelper.extractFailedKeysFromBatchResults(results, keys)

        assertThat(extracted).hasSize(0)
    }

    @Test
    fun `can extract execute failed code from result set`() {
        val results = intArrayOf(1, 1, 1, Statement.EXECUTE_FAILED, 1)
        val keys = listOf("a", "b", "c", "d", "e")

        val extracted = PreparedStatementHelper.extractFailedKeysFromBatchResults(results, keys)

        assertThat(extracted).hasSize(1)
        assertThat(extracted.first()).isEqualTo("d")
    }

    @Test
    fun `if results do not match commands throw exception`() {
        val results = intArrayOf(1, 0)
        val keys = listOf("a", "b", "c")

        assertThrows<PreparedStatementHelperException>(
            "Results from batch (size: 2) do not match commands in request (size 3"
        ) {
            PreparedStatementHelper.extractFailedKeysFromBatchResults(results, keys)
        }
    }
}