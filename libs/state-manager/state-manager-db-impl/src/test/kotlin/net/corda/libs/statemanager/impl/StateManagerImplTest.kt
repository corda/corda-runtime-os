package net.corda.libs.statemanager.impl

import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.metadata
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.repository.StateRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.PersistenceException

class StateManagerImplTest {
    private val stateRepository: StateRepository = mock()

    private val entityManager: EntityManager = mock {
        on { transaction } doReturn mock()
    }

    private val entityManagerFactory: EntityManagerFactory = mock {
        on { createEntityManager() } doReturn entityManager
    }

    private val stateManager = StateManagerImpl(
        stateRepository = stateRepository,
        entityManagerFactory = entityManagerFactory,
    )

    private val firstStateEntity = StateEntity("key1", "state1".toByteArray(), "{}", 1, Instant.now())
    private val firstState = State(
        firstStateEntity.key,
        firstStateEntity.value,
        firstStateEntity.version,
        metadata(),
        firstStateEntity.modifiedTime
    )

    private val secondStateEntity = StateEntity("key2", "state2".toByteArray(), "{}", 2, Instant.now())
    private val secondState = State(
        secondStateEntity.key,
        secondStateEntity.value,
        secondStateEntity.version,
        metadata(),
        secondStateEntity.modifiedTime
    )

    private val thirdStateEntity = StateEntity("key3", "state3".toByteArray(), "{}", 3, Instant.now())
    private val thirdState = State(
        thirdStateEntity.key,
        thirdStateEntity.value,
        thirdStateEntity.version,
        metadata(),
        thirdStateEntity.modifiedTime
    )
    @Test
    fun checkVersionAndPrepareEntitiesForPersistenceReturnsEmptyUnmatchedVersionsWhenAllEntitiesHaveCorrectVersionSet() {
        whenever(stateRepository.get(any(), any())).thenReturn(listOf(firstStateEntity, secondStateEntity))
        val result =
            stateManager.checkVersionAndPrepareEntitiesForPersistence(listOf(firstState, secondState), entityManager)

        assertThat(result.first).containsExactly(firstStateEntity, secondStateEntity)
        assertThat(result.second).isEmpty()
    }

    @Test
    fun checkVersionAndPrepareEntitiesForPersistenceReturnsEmptyMatchedVersionsWhenAllEntitiesHaveIncorrectVersionSet() {
        whenever(stateRepository.get(any(), any())).thenReturn(emptyList())
        val result =
            stateManager.checkVersionAndPrepareEntitiesForPersistence(listOf(firstState, secondState), entityManager)

        assertThat(result.first).isEmpty()
        assertThat(result.second).containsExactly(
            entry(firstState.key, firstState),
            entry(secondState.key, secondState)
        )
    }

    @Test
    fun checkVersionAndPrepareEntitiesForPersistenceReturnsCorrectlyWhenSomeVersionsMatchAndSomeVersionsDoNotMatch() {
        whenever(stateRepository.get(any(), any())).thenReturn(listOf(firstStateEntity))
        val result =
            stateManager.checkVersionAndPrepareEntitiesForPersistence(listOf(firstState, secondState), entityManager)

        assertThat(result.first).containsExactly(firstStateEntity)
        assertThat(result.second).containsExactly(entry(secondState.key, secondState))
    }

    @Test
    fun createReturnsEmptyMapWhenAllInsertsSucceed() {
        assertThat(stateManager.create(listOf(firstState, secondState))).isEmpty()
        verify(stateRepository).create(entityManager, firstStateEntity)
        verify(stateRepository).create(entityManager, secondStateEntity)
    }

    @Test
    fun createReturnsMapWithStatesThatAlreadyExist() {
        val persistenceException = PersistenceException("Mock Exception")
        doThrow(persistenceException).whenever(stateRepository).create(entityManager, firstStateEntity)

        assertThat(stateManager.create(listOf(firstState, secondState)))
            .containsExactly(entry(firstState.key, persistenceException))
        verify(stateRepository).create(entityManager, firstStateEntity)
        verify(stateRepository).create(entityManager, secondStateEntity)
    }

    @Test
    fun updateOnlyPersistsStatesWithMatchingVersions() {
        whenever(stateRepository.get(any(), any())).thenReturn(listOf(firstStateEntity, thirdStateEntity))
        val result = stateManager.update(listOf(firstState, secondState, thirdState))

        assertThat(result).containsExactly(entry(secondState.key, secondState))
        verify(stateRepository).update(entityManager, listOf(firstStateEntity, thirdStateEntity))
        verify(stateRepository, never()).update(entityManager, listOf(secondStateEntity))
    }

    @Test
    fun deleteOnlyPersistsStatesWithMatchingVersions() {
        whenever(stateRepository.get(any(), any())).thenReturn(listOf(firstStateEntity, thirdStateEntity))
        val result = stateManager.delete(listOf(firstState, secondState, thirdState))

        assertThat(result).containsExactly(entry(secondState.key, secondState))
        verify(stateRepository).delete(entityManager, listOf(firstStateEntity.key, thirdStateEntity.key))
        verify(stateRepository, never()).delete(entityManager, listOf(secondStateEntity.key))
    }
}
