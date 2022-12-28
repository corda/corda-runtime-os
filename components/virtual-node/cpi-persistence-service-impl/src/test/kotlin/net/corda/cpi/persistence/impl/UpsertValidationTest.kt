package net.corda.cpi.persistence.impl

import net.corda.cpi.persistence.CpiPersistenceDuplicateCpiException
import net.corda.cpi.persistence.CpiPersistenceValidationException
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.lifecycle.LifecycleCoordinatorFactory
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.TypedQuery

@Disabled("disabled for POC")
class UpsertValidationTest {

    private val coordinatorFactory: LifecycleCoordinatorFactory = mock()
    private val dbConnectionManager: DbConnectionManager = mock()

    private fun createMockEntityManagerFactory(queryResult: List<CpiMetadataEntity>): EntityManagerFactory {
        val query = mock<TypedQuery<CpiMetadataEntity>> {
            on { it.setParameter(any<String>(), any<String>()) }.doReturn(it)
            on { it.resultList }.doReturn(queryResult)
        }
        val tx = mock<EntityTransaction>()
        val mockEntityManager = mock<EntityManager> {
            on { it.createQuery(any(), any<Class<CpiMetadataEntity>>()) } doReturn (query)
            on { it.transaction } doReturn (tx)
        }
        return mock {
            on { it.createEntityManager() } doReturn (mockEntityManager)
        }
    }
    @Test
    fun `succeeds with unique cpi`() {
        val requiredName = "aaa"
        val requiredVersion = "1.0"
        val hash = "DUMMY:1234567890"
        val groupId = "ABC"

        whenever(dbConnectionManager.getClusterEntityManagerFactory())
            .thenReturn(createMockEntityManagerFactory(emptyList()))

        val p = DatabaseCpiPersistence(coordinatorFactory, dbConnectionManager)

        assertDoesNotThrow {
            p.validateCanUpsertCpi(requiredName, hash, requiredVersion, groupId, false)
        }
    }

    @Test
    fun `succeeds force upload when version exact`() {
        val requiredName = "aaa"
        val requiredVersion = "1.0"
        val hash = "DUMMY:1234567890"
        val groupId = "ABC"

        val meta = mock<CpiMetadataEntity> {
            on { it.version }.doReturn(requiredVersion)
            on { it.groupId }.doReturn(groupId)
        }

        whenever(dbConnectionManager.getClusterEntityManagerFactory())
            .doReturn(createMockEntityManagerFactory(listOf(meta)))

        val p = DatabaseCpiPersistence(coordinatorFactory, dbConnectionManager)

        assertDoesNotThrow {
            p.validateCanUpsertCpi(requiredName, hash, requiredVersion, groupId, true)
        }
    }

    @Test
    fun `fails force upload when version correct but groupId different to previous`() {
        val requiredName = "aaa"
        val requiredVersion = "1.0"
        val hash = "DUMMY:1234567890"
        val groupId = "ABC"

        val meta = mock<CpiMetadataEntity> {
            on { it.version }.doReturn(requiredVersion)
            on { it.groupId }.doReturn("foo")
        }

        whenever(dbConnectionManager.getClusterEntityManagerFactory())
            .doReturn(createMockEntityManagerFactory(listOf(meta)))

        val p = DatabaseCpiPersistence(coordinatorFactory, dbConnectionManager)


        assertThrows<CpiPersistenceValidationException> {
            p.validateCanUpsertCpi(requiredName, hash, requiredVersion, groupId, true)
        }
    }

    @Test
    fun `fails force upload when version is different`() {
        val requiredName = "aaa"
        val requiredVersion = "1.0"
        val hash = "DUMMY:1234567890"
        val groupId = "ABC"

        val meta = mock<CpiMetadataEntity> {
            on { version }.doReturn("2.0")
        }

        whenever(dbConnectionManager.getClusterEntityManagerFactory())
            .doReturn(createMockEntityManagerFactory(listOf(meta)))

        val p = DatabaseCpiPersistence(coordinatorFactory, dbConnectionManager)

        assertThrows<CpiPersistenceValidationException> {
            p.validateCanUpsertCpi(requiredName, hash, requiredVersion, groupId, true)
        }
    }

    @Test
    fun `succeeds upload when version different`() {
        val requiredName = "aaa"
        val requiredVersion = "1.0"
        val hash = "DUMMY:1234567890"
        val groupId = "ABC"

        val meta = mock<CpiMetadataEntity> {
            on { version }.doReturn("2.0")
        }

        whenever(dbConnectionManager.getClusterEntityManagerFactory())
            .doReturn(createMockEntityManagerFactory(listOf(meta)))

        val p = DatabaseCpiPersistence(coordinatorFactory, dbConnectionManager)

        assertDoesNotThrow {
            p.validateCanUpsertCpi(requiredName, hash, requiredVersion, groupId, false)
        }
    }

    @Test
    fun `fails upload when version is same`() {
        val requiredName = "aaa"
        val requiredVersion = "1.0"
        val hash = "DUMMY:1234567890"
        val groupId = "ABC"

        val meta = mock<CpiMetadataEntity> {
            on { version }.doReturn(requiredVersion)
        }

        whenever(dbConnectionManager.getClusterEntityManagerFactory())
            .doReturn(createMockEntityManagerFactory(listOf(meta)))

        val p = DatabaseCpiPersistence(coordinatorFactory, dbConnectionManager)

        assertThrows<CpiPersistenceDuplicateCpiException> {
            p.validateCanUpsertCpi(requiredName, hash, requiredVersion, groupId, false)
        }
    }
}