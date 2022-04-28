package net.corda.messagebus.db.persistence

import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import javax.persistence.EntityManagerFactory

class DBAccessTest {
    @Test
    fun `DBAccess closes entity manager factory on close`() {
        val emf = mock<EntityManagerFactory>()
        val dbAccess = DBAccess(emf)
        dbAccess.close()
        verify(emf).close()
    }
}
