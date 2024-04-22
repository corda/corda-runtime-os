package net.corda.orm.impl

import net.corda.orm.impl.PersistenceExceptionCategorizerImpl.Companion.CONNECTION_CLOSED_MESSAGE
import net.corda.orm.PersistenceExceptionType
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.QueryException
import org.hibernate.ResourceClosedException
import org.hibernate.SessionException
import org.hibernate.TransactionException
import org.hibernate.cache.CacheException
import org.hibernate.exception.ConstraintViolationException
import org.hibernate.exception.GenericJDBCException
import org.hibernate.exception.JDBCConnectionException
import org.hibernate.exception.LockAcquisitionException
import org.hibernate.exception.SQLGrammarException
import org.hibernate.procedure.NoSuchParameterException
import org.hibernate.procedure.ParameterMisuseException
import org.hibernate.property.access.spi.PropertyAccessException
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.sql.SQLException
import java.sql.SQLTransientConnectionException
import java.util.stream.Stream
import javax.persistence.EntityExistsException
import javax.persistence.EntityNotFoundException
import javax.persistence.LockTimeoutException
import javax.persistence.NonUniqueResultException
import javax.persistence.OptimisticLockException
import javax.persistence.PessimisticLockException
import javax.persistence.QueryTimeoutException
import javax.persistence.RollbackException
import javax.persistence.TransactionRequiredException

class PersistenceExceptionCategorizerImplTest {

    private companion object {

        private const val DUMMY_MESSAGE = "dummy"
        private const val DUMMY_SQL = "sql"
        private val DUMMY_SQL_EXCEPTION = SQLException()

        @JvmStatic
        fun transientPersistenceExceptions(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(LockTimeoutException()),
                Arguments.of(OptimisticLockException()),
                Arguments.of(PessimisticLockException()),
                Arguments.of(QueryTimeoutException()),
                Arguments.of(RollbackException()),
                Arguments.of(org.hibernate.PessimisticLockException(DUMMY_MESSAGE, DUMMY_SQL_EXCEPTION, DUMMY_SQL)),
                Arguments.of(org.hibernate.QueryTimeoutException(DUMMY_MESSAGE, DUMMY_SQL_EXCEPTION, DUMMY_SQL)),
                Arguments.of(JDBCConnectionException(DUMMY_MESSAGE, DUMMY_SQL_EXCEPTION, DUMMY_SQL)),
                Arguments.of(LockAcquisitionException(DUMMY_MESSAGE, DUMMY_SQL_EXCEPTION, DUMMY_SQL)),
                Arguments.of(TransactionException(DUMMY_MESSAGE)),
                Arguments.of(CacheException(DUMMY_MESSAGE)),
                Arguments.of(SQLTransientConnectionException("Connection is not available, request timed out after")),
                Arguments.of(SQLException(CONNECTION_CLOSED_MESSAGE)),
                Arguments.of(SQLException(DUMMY_MESSAGE, "08001")),
                Arguments.of(SQLException(DUMMY_MESSAGE, "08003")),
                Arguments.of(SQLException(DUMMY_MESSAGE, "08004")),
                Arguments.of(SQLException(DUMMY_MESSAGE, "08006")),
                Arguments.of(SQLException(DUMMY_MESSAGE, "08007")),
                Arguments.of(SQLException(DUMMY_MESSAGE, "58030")),
            )
        }

        @JvmStatic
        fun platformPersistenceExceptions(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(EntityExistsException()),
                Arguments.of(EntityNotFoundException()),
                Arguments.of(NonUniqueResultException()),
                Arguments.of(SQLGrammarException(DUMMY_MESSAGE, DUMMY_SQL_EXCEPTION)),
                Arguments.of(GenericJDBCException(DUMMY_MESSAGE, DUMMY_SQL_EXCEPTION)),
                Arguments.of(QueryException(DUMMY_MESSAGE, DUMMY_SQL_EXCEPTION)),
                Arguments.of(NoSuchParameterException(DUMMY_MESSAGE)),
                Arguments.of(ParameterMisuseException(DUMMY_MESSAGE)),
                Arguments.of(PropertyAccessException(DUMMY_MESSAGE)),
                Arguments.of(ConstraintViolationException(DUMMY_MESSAGE, DUMMY_SQL_EXCEPTION, DUMMY_MESSAGE))
            )
        }

        @JvmStatic
        fun fatalPersistenceExceptions(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(TransactionRequiredException()),
                Arguments.of(ResourceClosedException(DUMMY_MESSAGE)),
                Arguments.of(SessionException(DUMMY_MESSAGE)),
            )
        }
    }

    private val persistenceExceptionCategorizer = PersistenceExceptionCategorizerImpl()

    @ParameterizedTest(name = "{0} is categorized as a transient persistence exception")
    @MethodSource("transientPersistenceExceptions")
    fun `transient persistence exceptions`(exception: Exception) {
        assertThat(persistenceExceptionCategorizer.categorize(exception)).isEqualTo(PersistenceExceptionType.TRANSIENT)
    }

    @ParameterizedTest(name = "{0} is categorized as a platform persistence exception")
    @MethodSource("platformPersistenceExceptions")
    fun `platform persistence exceptions`(exception: Exception) {
        assertThat(persistenceExceptionCategorizer.categorize(exception)).isEqualTo(PersistenceExceptionType.DATA_RELATED)
    }

    @ParameterizedTest(name = "{0} is categorized as a fatal persistence exception")
    @MethodSource("fatalPersistenceExceptions")
    fun `fatal persistence exceptions`(exception: Exception) {
        assertThat(persistenceExceptionCategorizer.categorize(exception)).isEqualTo(PersistenceExceptionType.FATAL)
    }

    @Test
    fun `unknown exceptions are categorized as platform`() {
        assertThat(persistenceExceptionCategorizer.categorize(Exception())).isEqualTo(PersistenceExceptionType.UNCATEGORIZED)
    }
}