package net.corda.orm

import net.corda.orm.utils.transactionExecutor
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import javax.persistence.EntityManager
import javax.persistence.EntityTransaction

class EntityManagerUtilsTest {

    @Test
    fun `when transaction and commit fails call close`() {
        val tx = mock<EntityTransaction>() {
            on { commit() } doThrow RuntimeException()
        }
        val em = mock<EntityManager>() {
            on { transaction } doReturn tx
        }

        try {
            transactionExecutor(em) {
                println("do")
            }
        }
        catch (e: Exception) {
            println("Caught $e")
        }

        verify(em, times(1)).close()
    }
}