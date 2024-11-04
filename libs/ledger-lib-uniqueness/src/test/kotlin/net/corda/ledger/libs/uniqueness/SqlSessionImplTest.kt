package net.corda.ledger.libs.uniqueness

import net.corda.db.core.PersistenceExceptionCategorizer
import net.corda.db.core.PersistenceExceptionType
import net.corda.ledger.libs.uniqueness.backingstore.impl.SqlSessionImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import javax.persistence.OptimisticLockException

class SqlSessionImplTest {
    private companion object {
        private const val MAX_ATTEMPTS = 10
    }

    val persistenceExceptionCategorizer = mock<PersistenceExceptionCategorizer>()

    val session = SqlSessionImpl(
        mock(),
        mock(),
        mock(),
        persistenceExceptionCategorizer,
        mock(),
    )

    @Test
    fun `Executing transaction retries upon data_related exception`() {
        whenever(persistenceExceptionCategorizer.categorize(any()))
            .thenReturn(PersistenceExceptionType.DATA_RELATED)
        var execCounter = 0
        assertThrows<IllegalStateException> {
            session.executeTransaction { _, _ ->
                execCounter++
                throw DummyException()
            }
        }
        assertThat(execCounter).isEqualTo(MAX_ATTEMPTS)
    }

    @Test
    fun `Executing transaction retries upon transient exception`() {
        whenever(persistenceExceptionCategorizer.categorize(any()))
            .thenReturn(PersistenceExceptionType.TRANSIENT)
        var execCounter = 0
        assertThrows<IllegalStateException> {
            session.executeTransaction { _, _ ->
                execCounter++
                throw DummyException()
            }
        }
        assertThat(execCounter).isEqualTo(MAX_ATTEMPTS)
    }

    @Test
    fun `Executing transaction does not retry upon uncategorized exception`() {
        whenever(persistenceExceptionCategorizer.categorize(any()))
            .thenReturn(PersistenceExceptionType.UNCATEGORIZED)
        var execCounter = 0
        assertThrows<DummyException> {
            session.executeTransaction { _, _ ->
                execCounter++
                throw DummyException()
            }
        }
        assertThat(execCounter).isEqualTo(1)
    }

    @Test
    fun `Executing transaction succeeds after transient failures`() {
        whenever(persistenceExceptionCategorizer.categorize(any()))
            .thenReturn(PersistenceExceptionType.TRANSIENT)
        val retryCnt = 3
        var execCounter = 0
        assertDoesNotThrow {
            session.executeTransaction { _, _ ->
                execCounter++
                if (execCounter < retryCnt) {
                    throw OptimisticLockException()
                }
            }
        }
        assertThat(execCounter).isEqualTo(retryCnt)
    }

    @Test
    fun `Executing transaction does not retry upon fatal exception`() {
        whenever(persistenceExceptionCategorizer.categorize(any()))
            .thenReturn(PersistenceExceptionType.FATAL)
        var execCounter = 0
        assertThrows<DummyException> {
            session.executeTransaction { _, _ ->
                execCounter++
                throw DummyException()
            }
        }
        assertThat(execCounter).isEqualTo(1)
    }

    class DummyException(message: String = "") : Exception(message)
}
