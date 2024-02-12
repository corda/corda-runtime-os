package net.corda.libs.statemanager.impl.repository.impl

import net.corda.libs.statemanager.api.State
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Statement.EXECUTE_FAILED
import java.sql.Statement.SUCCESS_NO_INFO

@Disabled
class StateRepositoryImplTest {

    private val queryProvider = mock<QueryProvider>()
    private val connection = mock<Connection>()
    private val statement = mock<PreparedStatement>()

    @BeforeEach
    fun setup() {
        whenever(connection.prepareStatement(any())).thenReturn(statement)
    }

    @Test
    fun `delete returns inputs as failed if they hit a JDBC error`() {
        val states = createStates(4)
        whenever(statement.executeBatch()).thenReturn(
            arrayOf(1, 0, SUCCESS_NO_INFO, EXECUTE_FAILED).toIntArray()
        )
        whenever(queryProvider.deleteStatesByKey).thenReturn("")
        val repository = StateRepositoryImpl(queryProvider)
        val failed = repository.delete(connection, states)
        assertThat(failed.size).isEqualTo(3)
    }

    private fun createStates(numStates: Int): Collection<State> {
        return (1..numStates).map {
            State(
                "foo_$it",
                "".toByteArray()
            )
        }
    }
}
