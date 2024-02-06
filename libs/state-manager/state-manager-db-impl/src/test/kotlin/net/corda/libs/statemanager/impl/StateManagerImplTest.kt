package net.corda.libs.statemanager.impl

import net.corda.db.core.CloseableDataSource
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.metadata
import net.corda.libs.statemanager.impl.metrics.MetricsRecorder
import net.corda.libs.statemanager.impl.metrics.MetricsRecorderImpl
import net.corda.libs.statemanager.impl.repository.StateRepository
import net.corda.lifecycle.LifecycleCoordinatorFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.time.Instant

class StateManagerImplTest {
    private val connection: Connection = mock()
    private val stateRepository: StateRepository = mock()
    private val metricsRecorder: MetricsRecorder = spy<MetricsRecorderImpl>()
    private val dataSource: CloseableDataSource = mock {
        on { connection } doReturn connection
    }
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {}
    private val stateManager =
        StateManagerImpl(lifecycleCoordinatorFactory, dataSource, stateRepository, metricsRecorder)

    private val stateOne = State("key1", "state1".toByteArray(), 1, metadata(), Instant.now())
    private val stateTwo = State("key2", "state2".toByteArray(), 2, metadata(), Instant.now())
    private val stateThree = State("key3", "state3".toByteArray(), 3, metadata(), Instant.now())

    private fun State.newVersion() = State(key, value, version + 1, metadata, modifiedTime)

    @Test
    fun createReturnsEmptyMapWhenAllInsertsSucceed() {
        doReturn(setOf(stateOne.key, stateTwo.key))
            .whenever(stateRepository).create(connection, listOf(stateOne, stateTwo))
        assertThat(stateManager.create(listOf(stateOne, stateTwo))).isEmpty()
        verify(stateRepository).create(connection, listOf(stateOne, stateTwo))
    }

    @Test
    fun `create throws an error if two states with the same key are created`() {
        val states = listOf(
            stateOne,
            stateTwo,
            stateOne,
        )

        assertThatThrownBy {
            stateManager.create(states)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Creating multiple states with the same key is not supported")
    }

    @Test
    fun createReturnsMapWithStatesThatAlreadyExist() {
        doReturn(setOf(stateTwo.key))
            .whenever(stateRepository).create(connection, listOf(stateOne, stateTwo))

        assertThat(stateManager.create(listOf(stateOne, stateTwo)))
            .contains(stateOne.key)
        verify(stateRepository).create(connection, listOf(stateOne, stateTwo))
    }

    @Test
    fun createReturnsEmptyWithEmptyInput() {
        assertThat(stateManager.create(listOf())).isEmpty()
        verify(stateRepository, never()).create(any(), any())
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
            .thenReturn(
                StateRepository.StateUpdateSummary(
                    listOf(stateTwo.key, stateTwo.key, stateThree.key),
                    emptyList()
                )
            )

        val result = stateManager.update(listOf(stateOne, stateTwo, stateThree))
        assertThat(result).isEmpty()
        verify(stateRepository).update(connection, listOf(stateOne, stateTwo, stateThree))
        verifyNoMoreInteractions(stateRepository)
    }

    @Test
    fun updateReturnsLatestPersistedViewForStatesThatFailedOptimisticLockingCheck() {
        val persistedStateTwo = stateTwo.newVersion()
        whenever(stateRepository.get(any(), any())).thenReturn(listOf(persistedStateTwo))
        whenever(stateRepository.update(any(), any()))
            .thenReturn(
                StateRepository.StateUpdateSummary(
                    listOf(stateTwo.key, stateThree.key),
                    listOf(stateTwo.key)
                )
            )

        val result = stateManager.update(listOf(stateOne, stateTwo, stateThree))
        assertThat(result).containsExactly(entry(persistedStateTwo.key, persistedStateTwo))
        verify(stateRepository).get(connection, listOf(stateTwo.key))
        verify(stateRepository).update(connection, listOf(stateOne, stateTwo, stateThree))
        verifyNoMoreInteractions(stateRepository)
    }

    @Test
    fun `update returns null for states that failed because they were already deleted`() {
        val persistedStateTwo = stateTwo.newVersion()
        whenever(stateRepository.get(any(), any())).thenReturn(listOf(persistedStateTwo))
        whenever(stateRepository.update(any(), any()))
            .thenReturn(
                StateRepository.StateUpdateSummary(
                    listOf(stateTwo.key),
                    listOf(stateTwo.key, stateThree.key)
                )
            )

        val result = stateManager.update(listOf(stateOne, stateTwo, stateThree))
        assertThat(result).containsExactly(
            entry(persistedStateTwo.key, persistedStateTwo),
            entry(stateThree.key, null)
        )
        verify(stateRepository).get(connection, listOf(stateTwo.key, stateThree.key))
        verify(stateRepository).update(connection, listOf(stateOne, stateTwo, stateThree))
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

        val result = stateManager.delete(listOf(stateOne, stateTwo, stateThree))
        assertThat(result).isEmpty()
        verify(stateRepository).delete(connection, listOf(stateOne, stateTwo, stateThree))
        verifyNoMoreInteractions(stateRepository)
    }

    @Test
    fun deleteReturnsLatestPersistedViewForStatesThatFailedOptimisticLockingCheck() {
        val persistedStateThree = stateThree.newVersion()
        whenever(stateRepository.get(any(), any())).thenReturn(listOf(persistedStateThree))
        whenever(stateRepository.delete(any(), any())).thenReturn(listOf(persistedStateThree.key))

        val result = stateManager.delete(listOf(stateOne, stateTwo, stateThree))
        assertThat(result).containsExactly(entry(persistedStateThree.key, persistedStateThree))
        verify(stateRepository).get(connection, listOf(stateThree.key))
        verify(stateRepository).delete(connection, listOf(stateOne, stateTwo, stateThree))
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
