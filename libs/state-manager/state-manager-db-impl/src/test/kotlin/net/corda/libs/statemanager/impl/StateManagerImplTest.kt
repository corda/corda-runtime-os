package net.corda.libs.statemanager.impl

import com.fasterxml.jackson.databind.ObjectMapper
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
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

    private val persistentStateOne = StateEntity("key1", "state1".toByteArray(), "{}", 1, Instant.now())
    private val apiStateOne = persistentStateOne.toState()
    private val persistentStateTwo = StateEntity("key2", "state2".toByteArray(), "{}", 2, Instant.now())
    private val apiStateTwo = persistentStateTwo.toState()
    private val persistentStateThree = StateEntity("key3", "state3".toByteArray(), "{}", 3, Instant.now())
    private val apiStateThree = persistentStateThree.toState()

    private fun StateEntity.toState() = State(key, value, version, metadata(), modifiedTime)

    private fun StateEntity.newVersion() = StateEntity(key, value, metadata, version + 1, modifiedTime)

    @Test
    fun checkVersionAndPrepareEntitiesForPersistenceReturnsEmptyUnmatchedVersionsWhenAllEntitiesHaveCorrectVersionSet() {
        whenever(stateRepository.get(any(), any())).thenReturn(listOf(persistentStateOne, persistentStateTwo))

        val result =
            stateManager.checkVersionAndPrepareEntitiesForPersistence(listOf(apiStateOne, apiStateTwo), entityManager)
        assertThat(result.first).containsExactly(persistentStateOne, persistentStateTwo)
        assertThat(result.second).isEmpty()
    }

    @Test
    fun checkVersionAndPrepareEntitiesForPersistenceReturnsEmptyMatchedVersionsWhenAllEntitiesHaveIncorrectVersionSet() {
        val moreUpToDateStateOne = persistentStateOne.newVersion()
        val moreUpToDateStateTwo = persistentStateTwo.newVersion()
        whenever(stateRepository.get(any(), any())).thenReturn(listOf(moreUpToDateStateOne, moreUpToDateStateTwo))

        val result =
            stateManager.checkVersionAndPrepareEntitiesForPersistence(listOf(apiStateOne, apiStateTwo), entityManager)
        assertThat(result.first).isEmpty()
        assertThat(result.second).containsExactlyInAnyOrderEntriesOf(
            mutableMapOf(
                apiStateOne.key to moreUpToDateStateOne.toState(),
                apiStateTwo.key to moreUpToDateStateTwo.toState(),
            )
        )
    }

    @Test
    fun checkVersionAndPrepareEntitiesForPersistenceReturnsCorrectlyWhenSomeVersionsMatchAndSomeVersionsDoNotMatch() {
        val moreUpToDateStateTwo = persistentStateTwo.newVersion()
        whenever(stateRepository.get(any(), any())).thenReturn(listOf(persistentStateOne, moreUpToDateStateTwo))
        val result = stateManager.checkVersionAndPrepareEntitiesForPersistence(
            listOf(apiStateOne, apiStateTwo, apiStateThree), entityManager
        )

        assertThat(result.first).containsExactly(persistentStateOne)
        assertThat(result.second).containsExactlyInAnyOrderEntriesOf(
            mutableMapOf(
                apiStateTwo.key to moreUpToDateStateTwo.toState(),
            )
        )
    }

    @Test
    fun createReturnsEmptyMapWhenAllInsertsSucceed() {
        assertThat(stateManager.create(listOf(apiStateOne, apiStateTwo))).isEmpty()
        verify(stateRepository).create(entityManager, persistentStateOne)
        verify(stateRepository).create(entityManager, persistentStateTwo)
    }

    @Test
    fun createReturnsMapWithStatesThatAlreadyExist() {
        val persistenceException = PersistenceException("Mock Exception")
        doThrow(persistenceException).whenever(stateRepository).create(entityManager, persistentStateOne)

        assertThat(stateManager.create(listOf(apiStateOne, apiStateTwo)))
            .containsExactly(entry(apiStateOne.key, persistenceException))
        verify(stateRepository).create(entityManager, persistentStateOne)
        verify(stateRepository).create(entityManager, persistentStateTwo)
    }

    @Test
    fun updateOnlyPersistsStatesWithMatchingVersions() {
        val moreUpToDateStateTwo = persistentStateTwo.newVersion()
        val persistentStateFour = StateEntity("key4", "state4".toByteArray(), "{}", 4, Instant.now())
        val apiStateFour = persistentStateFour.toState()
        whenever(stateRepository.get(any(), any())).thenReturn(
            listOf(persistentStateOne, moreUpToDateStateTwo, persistentStateFour)
        )

        val result = stateManager.update(listOf(apiStateOne, apiStateTwo, apiStateThree, apiStateFour))
        assertThat(result).containsExactly(entry(apiStateTwo.key, moreUpToDateStateTwo.toState()))
        verify(stateRepository).get(
            entityManager,
            listOf(apiStateOne.key, apiStateTwo.key, apiStateThree.key, apiStateFour.key)
        )
        verify(stateRepository).update(entityManager, listOf(persistentStateOne, persistentStateFour))
        verifyNoMoreInteractions(stateRepository)
    }

    @Test
    fun deleteOnlyPersistsStatesWithMatchingVersions() {
        val moreUpToDateStateTwo = persistentStateTwo.newVersion()
        val persistentStateFour = StateEntity("key4", "state4".toByteArray(), "{}", 4, Instant.now())
        val apiStateFour = persistentStateFour.toState()
        whenever(stateRepository.get(any(), any())).thenReturn(
            listOf(persistentStateOne, moreUpToDateStateTwo, persistentStateFour)
        )

        val result = stateManager.delete(listOf(apiStateOne, apiStateTwo, apiStateThree, apiStateFour))
        assertThat(result).containsExactly(entry(apiStateTwo.key, moreUpToDateStateTwo.toState()))
        verify(stateRepository).get(
            entityManager,
            listOf(apiStateOne.key, apiStateTwo.key, apiStateThree.key, apiStateFour.key)
        )
        verify(stateRepository).delete(entityManager, listOf(persistentStateOne.key, persistentStateFour.key))
        verifyNoMoreInteractions(stateRepository)
    }

    @Test
    fun convertJson() {
        val str = """
            {
             "foo": "bar",
             "hello": 123
            }
        """.trimIndent()

        val meta = ObjectMapper().convertToMetadata(str)
        assertThat(meta["foo"]).isEqualTo("bar")
        assertThat(meta["hello"]).isEqualTo(123)
    }
}
