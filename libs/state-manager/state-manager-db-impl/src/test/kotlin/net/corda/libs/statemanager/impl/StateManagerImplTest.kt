package net.corda.libs.statemanager.impl

import net.corda.db.core.CloseableDataSource
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.metadata
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.repository.StateRepository
import net.corda.libs.statemanager.impl.repository.impl.StateManagerBatchingException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

class StateManagerImplTest {
    private val stateRepository: StateRepository = mock()

    private val entityManager: EntityManager = mock {
        on { transaction } doReturn mock()
    }

    private val entityManagerFactory: EntityManagerFactory = mock {
        on { createEntityManager() } doReturn entityManager
    }

    private val connection: Connection = mock { }

    private val dataSource: CloseableDataSource = mock {
        on { connection } doReturn connection
    }

    private val stateManager = StateManagerImpl(
        stateRepository = stateRepository,
        entityManagerFactory = entityManagerFactory,
        dataSource = dataSource,
    )

    private val persistentStateOne = StateEntity("key1", "state1".toByteArray(), "{}", 1, Instant.now())
    private val apiStateOne = persistentStateOne.toState()
    private val persistentStateTwo = StateEntity("key2", "state2".toByteArray(), "{}", 2, Instant.now())
    private val apiStateTwo = persistentStateTwo.toState()
    private val persistentStateThree = StateEntity("key3", "state3".toByteArray(), "{}", 3, Instant.now())
    private val apiStateThree = persistentStateThree.toState()

    private fun StateEntity.toState() = State(key, value, version, metadata(), modifiedTime)

    private fun StateEntity.newVersion() = StateEntity(key, value, metadata, version + 1, modifiedTime)

    @Test
    fun createReturnsEmptyListWhenAllInsertsSucceed() {
        assertThat(stateManager.create(listOf(apiStateOne, apiStateTwo))).isEmpty()
        verify(stateRepository).create(connection, listOf(persistentStateOne, persistentStateTwo))
    }

    @Test
    fun createReturnsFailedKeyOfStateThatAlreadyExist() {
        val argumentCaptor = argumentCaptor<Collection<StateEntity>>()

        whenever(stateRepository.create(eq(connection), argumentCaptor.capture())).thenReturn(listOf(apiStateOne.key))
        assertThat(stateManager.create(listOf(apiStateOne, apiStateTwo))).containsExactly((apiStateOne.key))

        val stateEntities = argumentCaptor.firstValue.toList()
        assertThat(stateEntities).hasSize(2)
        assertThat(stateEntities[0].key).isEqualTo(apiStateOne.key)
        assertThat(stateEntities[1].key).isEqualTo(apiStateTwo.key)
    }

    @Test
    fun `create states fails with batching exception and retries the failed state`() {
        val argumentCaptor1 = argumentCaptor<Collection<StateEntity>>()

        whenever(stateRepository.create(eq(connection), argumentCaptor1.capture()))
            .thenThrow(StateManagerBatchingException(listOf(persistentStateOne), "err"))

        whenever(stateRepository.create(eq(connection), eq(persistentStateOne))).thenReturn(true)

        assertThat(stateManager.create(listOf(apiStateOne, apiStateTwo))).isEmpty()

        val stateEntities = argumentCaptor1.firstValue.toList()
        assertThat(stateEntities).hasSize(2)
        assertThat(stateEntities[0].key).isEqualTo(apiStateOne.key)
        assertThat(stateEntities[1].key).isEqualTo(apiStateTwo.key)
    }

    @Test
    fun `create states fails both states, retries both states, fails and returns both keys`() {
        val argumentCaptor1 = argumentCaptor<Collection<StateEntity>>()

        whenever(stateRepository.create(eq(connection), argumentCaptor1.capture()))
            .thenThrow(StateManagerBatchingException(listOf(persistentStateOne, persistentStateTwo), "err"))

        whenever(stateRepository.create(eq(connection), eq(persistentStateOne))).thenReturn(false)
        whenever(stateRepository.create(eq(connection), eq(persistentStateTwo))).thenReturn(false)

        assertThat(stateManager.create(listOf(apiStateOne, apiStateTwo))).containsAll(listOf(apiStateOne.key, apiStateTwo.key))

        val stateEntities = argumentCaptor1.firstValue.toList()
        assertThat(stateEntities).hasSize(2)
        assertThat(stateEntities[0].key).isEqualTo(apiStateOne.key)
        assertThat(stateEntities[1].key).isEqualTo(apiStateTwo.key)
    }

    @Test
    fun updateReturnsEmptyMapWhenOptimisticLockingCheckSucceedsForAllStates() {
        whenever(stateRepository.update(any(), any())).thenReturn(emptyList())

        val result = stateManager.update(listOf(apiStateOne, apiStateTwo, apiStateThree))
        assertThat(result).isEmpty()
        verify(stateRepository).update(connection, listOf(persistentStateOne, persistentStateTwo, persistentStateThree))
        verifyNoMoreInteractions(stateRepository)
    }

    @Test
    fun updateReturnsLatestPersistedViewForStatesThatFailedOptimisticLockingCheck() {
        val persistedStateTwo = persistentStateTwo.newVersion()
        whenever(stateRepository.get(any(), any())).thenReturn(listOf(persistedStateTwo))
        whenever(stateRepository.update(any(), any())).thenReturn(listOf(persistedStateTwo.key))

        val result = stateManager.update(listOf(apiStateOne, apiStateTwo, apiStateThree))
        assertThat(result).containsExactly(entry(persistedStateTwo.key, persistedStateTwo.toState()))
        verify(stateRepository).get(entityManager, listOf(apiStateTwo.key))
        verify(stateRepository).update(connection, listOf(persistentStateOne, persistentStateTwo, persistentStateThree))
        verifyNoMoreInteractions(stateRepository)
    }

    @Test
    fun deleteReturnsEmptyMapWhenOptimisticLockingCheckSucceedsForAllStates() {
        whenever(stateRepository.delete(any(), any())).thenReturn(emptyList())

        val result = stateManager.delete(listOf(apiStateOne, apiStateTwo, apiStateThree))
        assertThat(result).isEmpty()
        verify(stateRepository).delete(connection, listOf(persistentStateOne, persistentStateTwo, persistentStateThree))
        verifyNoMoreInteractions(stateRepository)
    }

    @Test
    fun deleteReturnsLatestPersistedViewForStatesThatFailedOptimisticLockingCheck() {
        val persistedStateThree = persistentStateThree.newVersion()
        whenever(stateRepository.get(any(), any())).thenReturn(listOf(persistedStateThree))
        whenever(stateRepository.delete(any(), any())).thenReturn(listOf(persistedStateThree.key))

        val result = stateManager.delete(listOf(apiStateOne, apiStateTwo, apiStateThree))
        assertThat(result).containsExactly(entry(persistedStateThree.key, persistedStateThree.toState()))
        verify(stateRepository).get(entityManager, listOf(apiStateThree.key))
        verify(stateRepository).delete(connection, listOf(persistentStateOne, persistentStateTwo, persistentStateThree))
        verifyNoMoreInteractions(stateRepository)
    }
}
