package net.corda.uniqueness.backingstore.impl

import net.corda.crypto.core.bytes
import net.corda.crypto.testkit.SecureHashUtils.randomSecureHash
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.CloseableDataSource
import net.corda.ledger.libs.uniqueness.backingstore.BackingStoreMetricsFactory
import net.corda.ledger.libs.uniqueness.backingstore.impl.DefaultSqlQueryProvider
import net.corda.ledger.libs.uniqueness.backingstore.impl.backingStoreObjectMapper
import net.corda.ledger.libs.uniqueness.data.UniquenessHoldingIdentity
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.uniqueness.backingstore.impl.osgi.SQLBackingStoreOsgiImpl
import net.corda.uniqueness.backingstore.impl.osgi.UniquenessSecureHashFactoryOsgiImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorMalformedRequestImpl
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorMalformedRequest
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.Session
import org.hibernate.internal.SessionImpl
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.Calendar
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction

class SQLBackingStoreOsgiImplTest {
    private val entityManager = mock<EntityManager>()
    private val entityTransaction = mock<EntityTransaction>()
    private val entityManagerFactory = mock<EntityManagerFactory>()
    private val dummyDataSource = mock<CloseableDataSource>()
    private val jpaEntitiesRegistry = mock<JpaEntitiesRegistry>()
    private val dbConnectionManager = mock<DbConnectionManager>()
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>()
    private val metricsFactory = mock<BackingStoreMetricsFactory>()

    // Consider mocking this in the future. For now, we are not mocking this since the functionality didn't change,
    // we just exported the logic to a class
    private val secureHashFactory = UniquenessSecureHashFactoryOsgiImpl()

    private val backingStore = SQLBackingStoreOsgiImpl(
        jpaEntitiesRegistry,
        dbConnectionManager,
        virtualNodeInfoReadService,
        metricsFactory,
        secureHashFactory
    )

    private val groupId = UUID.randomUUID().toString()
    private val notaryRepIdentity = createTestHoldingIdentity("C=GB, L=London, O=NotaryRep1", groupId).let {
        UniquenessHoldingIdentity(it.x500Name, it.groupId, it.shortHash, it.hash)
    }
    private val mockConnection = mock<Connection>()

    @Suppress("ComplexMethod")
    @BeforeEach
    fun init() {
        val dummySession = mock<SessionImpl>().apply {
            whenever(connection()) doReturn mockConnection
        }

        whenever(entityManager.transaction) doReturn entityTransaction
        whenever(entityManager.unwrap(Session::class.java)) doReturn dummySession

        whenever(entityManagerFactory.createEntityManager()) doReturn entityManager

        whenever(dummyDataSource.connection) doReturn mock<Connection>()

        whenever(jpaEntitiesRegistry.get(any())) doReturn mock<JpaEntitiesSet>()

        whenever(dbConnectionManager.getClusterDataSource()) doReturn dummyDataSource
        whenever(dbConnectionManager.getOrCreateEntityManagerFactory(any<UUID>(), any(), any())) doReturn entityManagerFactory

        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).thenReturn(
            VirtualNodeInfo(
                holdingIdentity = mock(),
                cpiIdentifier = CpiIdentifier("", "", randomSecureHash()),
                vaultDmlConnectionId = UUID.randomUUID(),
                cryptoDmlConnectionId = UUID.randomUUID(),
                uniquenessDmlConnectionId = UUID.randomUUID(),
                timestamp = Instant.now()
            )
        )

        whenever(metricsFactory.recordDatabaseReadTime(any(), any())).doAnswer {  }
        whenever(metricsFactory.recordDatabaseCommitTime(any(), any())).doAnswer {  }

        whenever(metricsFactory.recordTransactionExecutionTime(any(), any())).doAnswer {  }
        whenever(metricsFactory.recordSessionExecutionTime(any(), any())).doAnswer {  }

        whenever(metricsFactory.recordTransactionAttempts(any(), any())).doAnswer {  }
        whenever(metricsFactory.incrementTransactionErrorCount(any(), any())).doAnswer {  }
    }

    @Test
    fun `Session always closes entity manager after use`() {
        backingStore.session(notaryRepIdentity) { }
        verify(mockConnection, times(1)).close()
    }

    @Test
    @Suppress("TooGenericExceptionThrown")
    fun `Session closes entity manager even when exception occurs`() {
        assertThrows<RuntimeException> {
            backingStore.session(notaryRepIdentity) { throw RuntimeException("test exception") }
        }
        verify(mockConnection, times(1)).close()
    }

    @Test
    fun `Executing transaction runs with transaction begin and commit`() {
        backingStore.session(notaryRepIdentity) { session ->
            session.executeTransaction { _, _ -> }
        }

        verify(mockConnection, times(1)).commit()
        verify(mockConnection, times(1)).close()
    }

    @Test
    fun `Throw if no error detail is available for a failed transaction`() {
        val txId = randomSecureHash()

        // NOTE: this isn't really a good unit test as it's testing the side effects of the class's dependency
        // so ths mocking gets messy.
        // it would be better to just verify the expected queries are called,
        // then unit test the functions that do the parsing
        val sqlQueryProvider = DefaultSqlQueryProvider()
        val mockResultSet = mock<ResultSet> {
            on { next() }.thenReturn(true).thenReturn(false)
            on { getString(1) } doReturn (txId.algorithm)
            on { getBytes(2) } doReturn (txId.bytes)
            on { getString(3) } doReturn ("R")
            on { getTimestamp(eq(4), any<Calendar>())} doReturn (Timestamp.from(Instant.now()))
        }
        val mockPreparedStatement = mock<PreparedStatement> {
            on { executeQuery() } doReturn mockResultSet
        }
        whenever(mockConnection.prepareStatement(sqlQueryProvider.findTransactionDetailByKeyQuery()))
            .doReturn(mockPreparedStatement)

        val mockErrorResultSet = mock<ResultSet> {
            on { next() } doReturn false
        }
        val mockErrorPreparedStatement = mock<PreparedStatement> {
            on { executeQuery() } doReturn mockErrorResultSet
        }
        whenever(mockConnection.prepareStatement(sqlQueryProvider.findRejectedTransactionQuery()))
            .doReturn(mockErrorPreparedStatement)

        // Expect an exception because no error details is available from the mock.
        assertThrows<IllegalStateException> {
            backingStore.session(notaryRepIdentity) { session ->
                session.getTransactionDetails(List(1) { randomSecureHash() })
            }
        }
    }

    @Test
    fun `Retrieve correct failed status without exceptions when both tx details and rejection details are present`() {
        val txId = randomSecureHash()

        // NOTE: this isn't really a good unit test as it's testing the side effects of the class's dependency
        // so ths mocking gets messy.
        // it would be better to just verify the expected queries are called,
        // then unit test the functions that do the parsing
        val sqlQueryProvider = DefaultSqlQueryProvider()
        val mockResultSet = mock<ResultSet> {
            on { next() }.thenReturn(true).thenReturn(false)
            on { getString(1) } doReturn (txId.algorithm)
            on { getBytes(2) } doReturn (txId.bytes)
            on { getString(3) } doReturn ("R")
            on { getTimestamp(eq(4), any<Calendar>())} doReturn (Timestamp.from(Instant.now()))
        }
        val mockPreparedStatement = mock<PreparedStatement> {
            on { executeQuery() } doReturn mockResultSet
        }
        whenever(mockConnection.prepareStatement(sqlQueryProvider.findTransactionDetailByKeyQuery()))
            .doReturn(mockPreparedStatement)


        val error = backingStoreObjectMapper(secureHashFactory).writeValueAsBytes(
            UniquenessCheckErrorMalformedRequestImpl("Error")
        )
        val mockErrorResultSet = mock<ResultSet> {
            on { next() }.thenReturn(true).thenReturn(false)
            on { getBytes(1) } doReturn error
        }
        val mockErrorPreparedStatement = mock<PreparedStatement> {
            on { executeQuery() } doReturn mockErrorResultSet
        }
        whenever(mockConnection.prepareStatement(sqlQueryProvider.findRejectedTransactionQuery()))
            .doReturn(mockErrorPreparedStatement)
        whenever(mockErrorResultSet.getBytes(1)).doReturn(error)

        backingStore.session(notaryRepIdentity) { session ->
            val txResult = session.getTransactionDetails(listOf(txId))[txId]?.result

            assertThat(txResult).isInstanceOf(UniquenessCheckResultFailure::class.java)
            assertThat((txResult as UniquenessCheckResultFailure).error)
                .isInstanceOf(UniquenessCheckErrorMalformedRequest::class.java)
        }
    }
}