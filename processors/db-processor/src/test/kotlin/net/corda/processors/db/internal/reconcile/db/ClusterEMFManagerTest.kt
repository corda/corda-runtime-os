package net.corda.processors.db.internal.reconcile.db

import net.corda.db.connection.manager.DbConnectionManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import javax.persistence.EntityManagerFactory

class ClusterEMFManagerTest {
    private val emf: EntityManagerFactory = mock()
    private val dbConnectionManager: DbConnectionManager = mock {
        on { getClusterEntityManagerFactory() } doReturn emf
    }
    private val emfManager = ClusterEMFManager(dbConnectionManager)

    @Test
    fun `Exception thrown if accessing EMF before calling start`() {
        assertThrows<IllegalArgumentException> {
            emfManager.emf
        }
    }

    @Test
    fun `Stopping manager before starting does nothing`() {
        assertDoesNotThrow { emfManager.stop() }
        verify(emf, never()).close()
    }

    @Test
    fun `Starting the manager create the EMF`() {
        emfManager.start()
        verify(dbConnectionManager).getClusterEntityManagerFactory()
    }

    @Test
    fun `EMF can be accessed after starting`() {
        emfManager.start()
        val result = assertDoesNotThrow {
            emfManager.emf
        }
        assertThat(result).isEqualTo(emf)
    }

    @Test
    fun `EMF is closed when stop is called`() {
        emfManager.start()
        emfManager.stop()
        verify(emf).close()
        assertThrows<IllegalArgumentException> { emfManager.emf }
    }

    @Test
    fun `EMF reference is removed when stop is called`() {
        emfManager.start()
        emfManager.stop()
        assertThrows<IllegalArgumentException> { emfManager.emf }
    }
}