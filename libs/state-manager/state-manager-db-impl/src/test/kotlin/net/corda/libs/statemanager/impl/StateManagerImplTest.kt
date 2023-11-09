package net.corda.libs.statemanager.impl

import net.corda.db.core.CloseableDataSource
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.metadata
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.repository.StateRepository
import net.corda.lifecycle.LifecycleCoordinatorFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.time.Instant
import javax.persistence.PersistenceException

class StateManagerImplTest {
    private val connection: Connection = mock { }
    private val stateRepository: StateRepository = mock()
    private val dataSource: CloseableDataSource = mock {
        on { connection } doReturn connection
    }
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {}
    private val stateManager = StateManagerImpl(lifecycleCoordinatorFactory, dataSource, stateRepository)

    private val persistentStateOne = StateEntity("key1", "state1".toByteArray(), "{}", 1, Instant.now())
    private val apiStateOne = persistentStateOne.toState()
    private val persistentStateTwo = StateEntity("key2", "state2".toByteArray(), "{}", 2, Instant.now())
    private val apiStateTwo = persistentStateTwo.toState()
    private val persistentStateThree = StateEntity("key3", "state3".toByteArray(), "{}", 3, Instant.now())
    private val apiStateThree = persistentStateThree.toState()

    private fun StateEntity.toState() = State(key, value, version, metadata(), modifiedTime)

    private fun StateEntity.newVersion() = StateEntity(key, value, metadata, version + 1, modifiedTime)

    @Test
    fun createReturnsEmptyMapWhenAllInsertsSucceed() {
        assertThat(stateManager.create(listOf(apiStateOne, apiStateTwo))).isEmpty()
        verify(stateRepository).create(connection, persistentStateOne)
        verify(stateRepository).create(connection, persistentStateTwo)
    }

    @Test
    fun createReturnsMapWithStatesThatAlreadyExist() {
        val persistenceException = PersistenceException("Mock Exception")
        doThrow(persistenceException).whenever(stateRepository).create(connection, persistentStateOne)

        assertThat(stateManager.create(listOf(apiStateOne, apiStateTwo)))
            .containsExactly(entry(apiStateOne.key, persistenceException))
        verify(stateRepository).create(connection, persistentStateOne)
        verify(stateRepository).create(connection, persistentStateTwo)
    }

    @Test
    fun getReturnsEmptyMapAndDoesNotInteractWithTheDatabaseWhenEmptyListOfKeysIsUsedAsInput() {
        val results = stateManager.get(emptyList())

        assertThat(results).isEmpty()
        verifyNoInteractions(dataSource)
    }

    @Test
    fun updateReturnsEmptyMapWhenOptimisticLockingCheckSucceedsForAllStates() {
        whenever(stateRepository.update(any(), any()))
            .thenReturn(StateRepository.StateUpdateSummary(
                listOf(apiStateTwo.key, apiStateTwo.key, apiStateThree.key),
                emptyList()
            ))

        val result = stateManager.update(listOf(apiStateOne, apiStateTwo, apiStateThree))
        assertThat(result).isEmpty()
        verify(stateRepository).update(connection, listOf(persistentStateOne, persistentStateTwo, persistentStateThree))
        verifyNoMoreInteractions(stateRepository)
    }

    @Test
    fun updateReturnsLatestPersistedViewForStatesThatFailedOptimisticLockingCheck() {
        val persistedStateTwo = persistentStateTwo.newVersion()
        whenever(stateRepository.get(any(), any())).thenReturn(listOf(persistedStateTwo))
        whenever(stateRepository.update(any(), any()))
            .thenReturn(StateRepository.StateUpdateSummary(
                listOf(apiStateTwo.key, apiStateThree.key),
                listOf(apiStateTwo.key)
            ))

        val result = stateManager.update(listOf(apiStateOne, apiStateTwo, apiStateThree))
        assertThat(result).containsExactly(entry(persistedStateTwo.key, persistedStateTwo.toState()))
        verify(stateRepository).get(connection, listOf(apiStateTwo.key))
        verify(stateRepository).update(connection, listOf(persistentStateOne, persistentStateTwo, persistentStateThree))
        verifyNoMoreInteractions(stateRepository)
    }

    @Test
    fun updateReturnsEmptyMapAndDoesNotInteractWithTheDatabaseWhenEmptyListOfStatesIsUsedAsInput() {
        val failedUpdates = assertDoesNotThrow { stateManager.update(emptyList()) }

        assertThat(failedUpdates).isEmpty()
        verifyNoInteractions(dataSource)
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
        verify(stateRepository).get(connection, listOf(apiStateThree.key))
        verify(stateRepository).delete(connection, listOf(persistentStateOne, persistentStateTwo, persistentStateThree))
        verifyNoMoreInteractions(stateRepository)
    }

    @Test
    fun deleteReturnsEmptyAndDoesNotInteractWithTheDatabaseWhenEmptyListOfStatesIsUsedInput() {
        val failedDeletes = assertDoesNotThrow { stateManager.delete(emptyList()) }

        assertThat(failedDeletes).isEmpty()
        verifyNoInteractions(dataSource)
    }

    @Test
    fun findByMetadataMatchingAllReturnsEmptyMapAndDoesNotInteractWithTheDatabaseWhenEmptyListOfFiltersIsUsedInput() {
        val result = stateManager.findByMetadataMatchingAll(emptyList())

        assertThat(result).isEmpty()
        verifyNoInteractions(dataSource)
    }

    @Test
    fun findByMetadataMatchingAnyReturnsEmptyMapAndDoesNotInteractWithTheDatabaseWhenEmptyListOfFiltersIsUsedInput() {
        val result = stateManager.findByMetadataMatchingAny(emptyList())

        assertThat(result).isEmpty()
        verifyNoInteractions(dataSource)
    }
}
