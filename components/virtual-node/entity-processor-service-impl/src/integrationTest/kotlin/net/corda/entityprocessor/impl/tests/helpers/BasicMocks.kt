package net.corda.entityprocessor.impl.tests.helpers

import net.corda.db.connection.manager.DbConnectionManager
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.osgi.service.component.ComponentContext
import javax.persistence.Entity
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction

/** Most **basic** mocks possible. */
object BasicMocks {
    fun dbConnectionManager(): DbConnectionManager {
        val mockEntityManagerFactory = Mockito.mock(EntityManagerFactory::class.java)
        val dbm = Mockito.mock(DbConnectionManager::class.java)
        Mockito.doReturn(mockEntityManagerFactory).`when`(dbm).createEntityManagerFactory(
            MockitoHelper.anyObject(),
            MockitoHelper.anyObject()
        )

        Mockito.doReturn(entityManager()).`when`(mockEntityManagerFactory).createEntityManager()

        return dbm
    }

    fun componentContext() = Mockito.mock(ComponentContext::class.java)!!

    fun entityManager():EntityManager {
        val em = Mockito.mock(EntityManager::class.java)!!
        val t = Mockito.mock(EntityTransaction::class.java)
        whenever(em.transaction).thenReturn(t)
        return em
    }
}
