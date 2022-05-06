package net.corda.entityprocessor.impl.tests.helpers

import net.corda.db.connection.manager.DbConnectionManager
import org.mockito.Mockito
import org.osgi.service.component.ComponentContext
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

/** Most **basic** mocks possible. */
object BasicMocks {
    fun dbConnectionManager(): DbConnectionManager {
        val mockEntityManagerFactory = Mockito.mock(EntityManagerFactory::class.java)
        val dbm = Mockito.mock(DbConnectionManager::class.java)
        Mockito.doReturn(mockEntityManagerFactory).`when`(dbm).createEntityManagerFactory(
            MockitoHelper.anyObject(),
            MockitoHelper.anyObject()
        )
        return dbm
    }

    fun componentContext() = Mockito.mock(ComponentContext::class.java)!!

    fun entityManager() = Mockito.mock(EntityManager::class.java)!!
}
