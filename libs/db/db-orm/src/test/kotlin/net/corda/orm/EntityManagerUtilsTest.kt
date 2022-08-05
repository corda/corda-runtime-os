package net.corda.orm

import net.corda.orm.utils.transactionExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import javax.persistence.EntityManager
import javax.persistence.EntityTransaction

@Suppress("TooGenericExceptionThrown")
class EntityManagerUtilsTest {

    @Test
    fun `when transaction and commit fails call close`() {
        val tx = mock<EntityTransaction>() {
            on { commit() } doThrow RuntimeException("exception when committing")
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

    @Test
    fun `when transaction and rollback fails call close`() {
        val tx = mock<EntityTransaction>() {
            on { rollback() } doThrow RuntimeException("exception when rolling back")
            on { rollbackOnly } doReturn (true)
        }
        val em = mock<EntityManager>() {
            on { transaction } doReturn tx
        }

        try {
            transactionExecutor(em) {
                throw RuntimeException("exception in block")
            }
        }
        catch (e: Exception) {
            println("Caught $e")
        }

        verify(em, times(1)).close()
    }

    @Test
    fun `when transaction close after commit`() {
        val tx = mock<EntityTransaction>()
        val em = mock<EntityManager>() {
            on { transaction } doReturn tx
        }

        transactionExecutor(em) {
            println("do")
        }

        verify(em, times(1)).close()
    }

    @Test
    fun `when transaction call begin`() {
        val tx = mock<EntityTransaction>()
        val em = mock<EntityManager>() {
            on { transaction } doReturn tx
        }

        transactionExecutor(em) {
            println("do")
        }

        verify(tx, times(1)).begin()
    }

    @Test
    fun `when transaction call commit`() {
        val tx = mock<EntityTransaction>()
        val em = mock<EntityManager>() {
            on { transaction } doReturn tx
        }

        transactionExecutor(em) {
            println("do")
        }

        verify(tx, times(1)).commit()
    }

    @Test
    fun `when transaction and something goes wrong set rollback`() {
        val tx = mock<EntityTransaction>()
        val em = mock<EntityManager>() {
            on { transaction } doReturn tx
        }

        try {
            transactionExecutor(em) {
                throw RuntimeException("exception in block")
            }
        }
        catch (e: Exception) {
            println("Caught $e")
        }

        verify(tx, times(1)).setRollbackOnly()
    }

    @Test
    fun `when transaction and set rollback roll back and don't commit`() {
        val tx = mock<EntityTransaction>() {
            on { rollbackOnly } doReturn true
        }
        val em = mock<EntityManager>() {
            on { transaction } doReturn tx
        }

        transactionExecutor(em) {
            println("do")
        }

        verify(tx, times(1)).rollback()
        verify(tx, times(0)).commit()
    }

    @Test
    fun `when transaction and something goes wrong rethrow`() {
        val tx = mock<EntityTransaction>()
        val em = mock<EntityManager>() {
            on { transaction } doReturn tx
        }

        val ex = RuntimeException("exception in block")
        val thrown = assertThrows<RuntimeException> {
            transactionExecutor(em) {
                throw ex
            }
        }

        assertThat(thrown).isEqualTo(ex)
    }
}