package net.corda.libs.statemanager.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.db.core.CloseableDataSource
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.metadata
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.repository.StateRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.time.Instant

class StateOperationBuilderImplTest {

    private val connection = mock<Connection>()
    private val datasource = mock<CloseableDataSource> {
        on { connection } doReturn(connection)
    }
    private val repository = mock<StateRepository>()
    private val objectMapper = mock<ObjectMapper>()

    private val persistentStateOne = StateEntity("key1", "state1".toByteArray(), "{}", 1, Instant.now())
    private val apiStateOne = persistentStateOne.toState()
    private val persistentStateTwo = StateEntity("key2", "state2".toByteArray(), "{}", 2, Instant.now())
    private val apiStateTwo = persistentStateTwo.toState()
    private val persistentStateThree = StateEntity("key3", "state3".toByteArray(), "{}", 3, Instant.now())
    private val apiStateThree = persistentStateThree.toState()

    private fun StateEntity.toState() = State(key, value, version, metadata(), modifiedTime)

    @Test
    fun `when states are created, updated and deleted, all operations appear in the same batch`() {
        setUpRepository(
            listOf(persistentStateOne.key),
            StateRepository.StateUpdateSummary(listOf(), listOf()),
            listOf(),
            listOf(persistentStateTwo, persistentStateThree)
        )
        val batch = StateOperationGroupBuilderImpl(datasource, repository, objectMapper)
        val failures = batch
            .create(apiStateOne)
            .update(apiStateTwo)
            .delete(apiStateThree)
            .execute()

        assertThat(failures).isEmpty()
    }

    @Test
    fun `when creating a new state fails, the key appears in the output of execute`() {

    }

    @Test
    fun `when updating a state fails due to an optimistic lock check, the key and state appear in the output of execute`() {

    }

    @Test
    fun `when updating a state fails due to the state not existing, the key appears in the output of execute`() {

    }

    @Test
    fun `when deleting a state fails due to an optimistic locking check, the key and state appear in the output of execute`() {

    }

    @Test
    fun `when deleting a state fails due to the state not existing, no output is seen from execute`() {

    }

    @Test
    fun `when execute is called with no states, nothing happens`() {

    }

    @Test
    fun `when a state on the same key is added twice, the group builder throws an exception`() {

    }

    @Test
    fun `when execute is invoked twice, the second attempt fails`() {

    }

    @Test
    fun `when adding states to a group builder that has already been executed, an exception is thrown`() {

    }

    private fun setUpRepository(
        createOutput: Collection<String>,
        updateOutput: StateRepository.StateUpdateSummary,
        deleteOutput: Collection<String>,
        persistedStates: List<StateEntity>) {
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