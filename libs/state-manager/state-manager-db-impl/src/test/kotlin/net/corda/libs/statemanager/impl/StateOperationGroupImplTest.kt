package net.corda.libs.statemanager.impl

import net.corda.db.core.CloseableDataSource
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.metadata
import net.corda.libs.statemanager.impl.repository.StateRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.time.Instant

class StateOperationGroupImplTest {

    private val connection = mock<Connection>()
    private val datasource = mock<CloseableDataSource> {
        on { connection } doReturn(connection)
    }
    private val repository = mock<StateRepository>()

    private val stateOne = State("key1", "state1".toByteArray(), 1, metadata(), Instant.now())
    private val stateTwo = State("key2", "state2".toByteArray(), 2, metadata(), Instant.now())
    private val stateThree = State("key3", "state3".toByteArray(), 3, metadata(), Instant.now())

    @Test
    fun `when states are created, updated and deleted, all operations appear in the same group`() {
        setUpRepository(
            listOf(stateOne.key),
            StateRepository.StateUpdateSummary(listOf(), listOf()),
            listOf(),
            listOf(stateTwo, stateThree)
        )
        val group = StateOperationGroupImpl(datasource, repository)
        val failures = group
            .create(stateOne)
            .update(stateTwo)
            .delete(stateThree)
            .execute()

        assertThat(failures).isEmpty()
        verify(repository).create(connection, listOf(stateOne))
        verify(repository).update(connection, listOf(stateTwo))
        verify(repository).delete(connection, listOf(stateThree))
        verify(repository, times(1)).get(connection, listOf())
        verify(datasource, times(1)).connection
    }

    @Test
    fun `when creating a new state fails, the key and state appears in the output of execute`() {
        setUpRepository(
            listOf(),
            StateRepository.StateUpdateSummary(listOf(), listOf()),
            listOf(),
            listOf(stateOne, stateTwo, stateThree)
        )
        val group = StateOperationGroupImpl(datasource, repository)
        val failures = group
            .create(stateOne)
            .execute()
        assertThat(failures).containsKey(stateOne.key)
        assertThat(failures[stateOne.key]).isEqualTo(stateOne)
        verify(repository).create(connection, listOf(stateOne))
        verify(repository).update(connection, listOf())
        verify(repository).delete(connection, listOf())
        verify(repository).get(connection, listOf(stateOne.key))
    }

    @Test
    fun `when updating a state fails due to an optimistic lock check, the key and state appear in the output of execute`() {
        setUpRepository(
            listOf(stateOne.key),
            StateRepository.StateUpdateSummary(listOf(), listOf(stateTwo.key)),
            listOf(),
            listOf(stateTwo, stateThree)
        )
        val group = StateOperationGroupImpl(datasource, repository)
        val failures = group
            .update(stateTwo)
            .execute()

        assertThat(failures).containsKey(stateTwo.key)
        assertThat(failures[stateTwo.key]).isEqualTo(stateTwo)
        verify(repository).create(connection, listOf())
        verify(repository).update(connection, listOf(stateTwo))
        verify(repository).delete(connection, listOf())
        verify(repository).get(connection, listOf(stateTwo.key))
        verify(datasource, times(1)).connection
    }

    @Test
    fun `when updating a state fails due to the state not existing, the key appears in the output of execute`() {
        setUpRepository(
            listOf(stateOne.key),
            StateRepository.StateUpdateSummary(listOf(), listOf(stateTwo.key)),
            listOf(),
            listOf(stateThree)
        )
        val group = StateOperationGroupImpl(datasource, repository)
        val failures = group
            .update(stateTwo)
            .execute()

        assertThat(failures).containsKey(stateTwo.key)
        assertThat(failures[stateTwo.key]).isEqualTo(null)
        verify(repository).create(connection, listOf())
        verify(repository).update(connection, listOf(stateTwo))
        verify(repository).delete(connection, listOf())
        verify(repository).get(connection, listOf(stateTwo.key))
        verify(datasource, times(1)).connection
    }

    @Test
    fun `when deleting a state fails due to an optimistic locking check, the key and state appear in the output of execute`() {
        setUpRepository(
            listOf(stateOne.key),
            StateRepository.StateUpdateSummary(listOf(), listOf()),
            listOf(stateThree.key),
            listOf(stateThree)
        )
        val group = StateOperationGroupImpl(datasource, repository)
        val failures = group
            .delete(stateThree)
            .execute()

        assertThat(failures).containsKey(stateThree.key)
        assertThat(failures[stateThree.key]).isEqualTo(stateThree)
        verify(repository).create(connection, listOf())
        verify(repository).update(connection, listOf())
        verify(repository).delete(connection, listOf(stateThree))
        verify(repository).get(connection, listOf(stateThree.key))
        verify(datasource, times(1)).connection
    }

    @Test
    fun `when deleting a state fails due to the state not existing, no output is seen from execute`() {
        setUpRepository(
            listOf(stateOne.key),
            StateRepository.StateUpdateSummary(listOf(), listOf()),
            listOf(stateThree.key),
            listOf()
        )
        val group = StateOperationGroupImpl(datasource, repository)
        val failures = group
            .delete(stateThree)
            .execute()

        assertThat(failures).isEmpty()
        verify(repository).create(connection, listOf())
        verify(repository).update(connection, listOf())
        verify(repository).delete(connection, listOf(stateThree))
        verify(repository).get(connection, listOf(stateThree.key))
        verify(datasource, times(1)).connection
    }

    @Test
    fun `when execute is called with no states, nothing happens`() {
        setUpRepository()
        val group = StateOperationGroupImpl(datasource, repository)
        val failures = group
            .execute()

        assertThat(failures).isEmpty()
        verify(repository).create(connection, listOf())
        verify(repository).update(connection, listOf())
        verify(repository).delete(connection, listOf())
        verify(repository, times(1)).get(connection, listOf())
        verify(datasource, times(1)).connection
    }

    @Test
    fun `when a state on the same key is added twice, the group builder throws an exception`() {
        val group = StateOperationGroupImpl(datasource, repository)
        group.create(stateOne)
        assertThrows<IllegalArgumentException> {
            group.create(stateOne)
        }
        assertThrows<IllegalArgumentException> {
            group.update(stateOne)
        }
        assertThrows<IllegalArgumentException> {
            group.delete(stateOne)
        }
    }

    @Test
    fun `when execute is invoked twice, the second attempt fails`() {
        setUpRepository()
        val group = StateOperationGroupImpl(datasource, repository)
        group.execute()
        assertThrows<IllegalStateException> {
            group.execute()
        }
    }

    @Test
    fun `when adding states to a group builder that has already been executed, an exception is thrown`() {
        setUpRepository()
        val group = StateOperationGroupImpl(datasource, repository)
        group.execute()
        assertThrows<IllegalStateException> {
            group.create(stateOne)
        }
        assertThrows<IllegalStateException> {
            group.update(stateOne)
        }
        assertThrows<IllegalStateException> {
            group.delete(stateOne)
        }
    }

    private fun setUpRepository(
        createOutput: Collection<String> = listOf(),
        updateOutput: StateRepository.StateUpdateSummary = StateRepository.StateUpdateSummary(listOf(), listOf()),
        deleteOutput: Collection<String> = listOf(),
        persistedStates: List<State> = listOf()
    ) {
        val captor = argumentCaptor<Collection<String>>()
        whenever(repository.create(any(), any())).thenReturn(createOutput)
        whenever(repository.update(any(), any())).thenReturn(updateOutput)
        whenever(repository.delete(any(), any())).thenReturn(deleteOutput)
        whenever(repository.get(any(), captor.capture())).thenAnswer {
            val keys = captor.lastValue.toSet()
            persistedStates.filter { it.key in keys }
        }
    }
}
