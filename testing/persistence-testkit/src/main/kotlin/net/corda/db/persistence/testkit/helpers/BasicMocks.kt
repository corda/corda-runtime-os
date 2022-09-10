package net.corda.db.persistence.testkit.helpers

import net.corda.db.connection.manager.DbConnectionManager
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.osgi.service.component.ComponentContext
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction

/** Most **basic** mocks possible. */
object BasicMocks {
    fun dbConnectionManager(): DbConnectionManager {
        val mockEntityManagerFactory = mock(EntityManagerFactory::class.java)
        val dbm = mock(DbConnectionManager::class.java)
        Mockito.doReturn(mockEntityManagerFactory).`when`(dbm).createEntityManagerFactory(
            MockitoHelper.anyObject(),
            MockitoHelper.anyObject()
        )

        Mockito.doReturn(entityManager()).`when`(mockEntityManagerFactory).createEntityManager()

        return dbm
    }

    fun componentContext() = mock(ComponentContext::class.java)!!

    fun entityManager():EntityManager {
        val em = mock(EntityManager::class.java)!!
        val t = mock(EntityTransaction::class.java)
        whenever(em.transaction).thenReturn(t)
        return em
    }
}
