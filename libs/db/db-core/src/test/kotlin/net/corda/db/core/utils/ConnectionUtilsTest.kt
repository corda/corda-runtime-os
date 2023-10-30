package net.corda.db.core.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Connection

class ConnectionUtilsTest {

    private val connection: Connection = mock()

    @Test
    fun `when commit fails call close`() {
        whenever(connection.commit()).thenThrow(RuntimeException("exception when committing"))

        val e = assertThrows<RuntimeException> {
            println(
                connection.transaction { _ -> "block executed" }
            )
        }

        verify(connection).autoCommit = false
        verify(connection, times(1)).rollback()
        verify(connection, times(1)).close()

        assertThat(e.message).isEqualTo("exception when committing")
    }

    @Test
    fun `when commit and rollback both fail call close`() {
        whenever(connection.commit()).thenThrow(RuntimeException("exception when committing"))
        whenever(connection.rollback()).thenThrow(RuntimeException("exception when rolling back"))

        val e = assertThrows<RuntimeException> {
            println(
                connection.transaction { _ -> "block executed" }
            )
        }

        verify(connection).autoCommit = false
        verify(connection, times(1)).close()

        assertThat(e.message).isEqualTo("exception when rolling back")
    }

    @Test
    fun `transaction closes and returns output of block`() {
        val result = connection.transaction { _ -> "block executed" }

        verify(connection).autoCommit = false
        verify(connection, times(1)).commit()
        verify(connection, times(1)).close()

        assertThat(result).isEqualTo("block executed")
    }

    @Test
    fun `transaction rolls back and closes when block throws`() {
        @Suppress("TooGenericExceptionThrown")
        val e = assertThrows<RuntimeException> {
            connection.transaction { _ -> throw RuntimeException("block exception") }
        }

        verify(connection).autoCommit = false
        verify(connection, times(1)).rollback()
        verify(connection, times(1)).close()

        assertThat(e.message).isEqualTo("block exception")
    }
}