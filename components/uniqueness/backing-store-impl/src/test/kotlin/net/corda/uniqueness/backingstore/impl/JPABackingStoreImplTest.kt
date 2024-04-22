package net.corda.uniqueness.backingstore.impl

import net.corda.crypto.core.bytes
import net.corda.crypto.testkit.SecureHashUtils
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.CloseableDataSource
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.orm.PersistenceExceptionCategorizer
import net.corda.orm.PersistenceExceptionType
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.uniqueness.backingstore.BackingStore
import net.corda.uniqueness.datamodel.common.UniquenessConstants
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorMalformedRequestImpl
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorMalformedRequest
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.MultiIdentifierLoadAccess
import org.hibernate.Session
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.OptimisticLockException
import javax.persistence.TypedQuery

class JPABackingStoreImplTest {

    private companion object {
        private const val MAX_ATTEMPTS = 10
    }

    private lateinit var backingStore: BackingStore

    private val entityManager = mock<EntityManager>()
    private val entityTransaction = mock<EntityTransaction>()
    private val entityManagerFactory = mock<EntityManagerFactory>()
    private val dummyDataSource = mock<CloseableDataSource>()
    private val jpaEntitiesRegistry = mock<JpaEntitiesRegistry>()

    /* These lists act as the database, the data added to these lists will be returned by the multi loads */
    private val txnDetails = mutableListOf<UniquenessTransactionDetailEntity>()
    private val stateEntities = mutableListOf<UniquenessStateDetailEntity>()
    private val errorEntities = mutableListOf<UniquenessRejectedTransactionEntity>()

    private val dbConnectionManager = mock<DbConnectionManager>()
    private val persistenceExceptionCategorizer = mock<PersistenceExceptionCategorizer>()
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>()

    private val groupId = UUID.randomUUID().toString()
    private val notaryRepIdentity = createTestHoldingIdentity("C=GB, L=London, O=NotaryRep1", groupId)

    private val originatorX500Name = "C=GB, L=London, O=Alice"

    @Suppress("ComplexMethod")
    @BeforeEach
    fun init() {
        whenever(entityTransaction.isActive) doReturn true

        val stateMultiLoad = mock<MultiIdentifierLoadAccess<UniquenessStateDetailEntity>>().apply {
            whenever(multiLoad(any<List<UniquenessTxAlgoStateRefKey>>())) doReturn stateEntities
        }
        val txMultiLoad = mock<MultiIdentifierLoadAccess<UniquenessTransactionDetailEntity>>().apply {
            whenever(multiLoad(any<List<UniquenessTxAlgoIdKey>>())) doReturn txnDetails
        }

        val txnErrorQuery = mock<TypedQuery<UniquenessRejectedTransactionEntity>>().apply {
            whenever(setParameter(eq("txAlgo"), any())) doReturn this
            whenever(setParameter(eq("txId"), any())) doReturn this
            whenever(resultList) doReturn errorEntities
        }

        val dummySession = mock<Session>().apply {
            // No need do anything here as this will have no effect in a unit test
            whenever(setJdbcBatchSize(any())).thenAnswer { }

            whenever(byMultipleIds(UniquenessStateDetailEntity::class.java)) doReturn stateMultiLoad
            whenever(byMultipleIds(UniquenessTransactionDetailEntity::class.java)) doReturn txMultiLoad
        }

        whenever(entityManager.transaction) doReturn entityTransaction
        whenever(entityManager.unwrap(Session::class.java)) doReturn dummySession
        whenever(
            entityManager.createNamedQuery(
                "UniquenessRejectedTransactionEntity.select",
                UniquenessRejectedTransactionEntity::class.java
            )
        ) doReturn txnErrorQuery


        whenever(entityManagerFactory.createEntityManager()) doReturn entityManager

        whenever(dummyDataSource.connection) doReturn mock<Connection>()

        whenever(jpaEntitiesRegistry.get(any())) doReturn mock<JpaEntitiesSet>()

        whenever(dbConnectionManager.getClusterDataSource()) doReturn dummyDataSource
        whenever(dbConnectionManager.getOrCreateEntityManagerFactory(any<UUID>(), any(), any())) doReturn entityManagerFactory

        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).thenReturn(
            VirtualNodeInfo(
                holdingIdentity = mock(),
                cpiIdentifier = CpiIdentifier("", "", SecureHashUtils.randomSecureHash()),
                vaultDmlConnectionId = UUID.randomUUID(),
                cryptoDmlConnectionId = UUID.randomUUID(),
                uniquenessDmlConnectionId = UUID.randomUUID(),
                timestamp = Instant.now()
            )
        )

        backingStore = JPABackingStoreImpl(
            jpaEntitiesRegistry,
            dbConnectionManager,
            persistenceExceptionCategorizer,
            virtualNodeInfoReadService
        )
    }

    @Test
    fun `Session always closes entity manager after use`() {
        backingStore.session(notaryRepIdentity) { }
        Mockito.verify(entityManager, times(1)).close()
    }

    @Test
    @Suppress("TooGenericExceptionThrown")
    fun `Session closes entity manager even when exception occurs`() {
        assertThrows<RuntimeException> {
            backingStore.session(notaryRepIdentity) { throw RuntimeException("test exception") }
        }
        Mockito.verify(entityManager, times(1)).close()
    }

    @Test
    fun `Executing transaction runs with transaction begin and commit`() {
        backingStore.session(notaryRepIdentity) { session ->
            session.executeTransaction { _, _ -> }
        }

        Mockito.verify(entityTransaction, times(1)).begin()
        Mockito.verify(entityTransaction, times(1)).commit()
        Mockito.verify(entityManager, times(1)).close()
    }

    @Test
    fun `Throw if no error detail is available for a failed transaction`() {
        // Prepare a rejected transaction
        txnDetails.add(
            UniquenessTransactionDetailEntity(
                "SHA-256",
                "0xA1".toByteArray(),
                originatorX500Name,
                LocalDate.parse("2099-12-12").atStartOfDay().toInstant(ZoneOffset.UTC),
                LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC),
                UniquenessConstants.RESULT_REJECTED_REPRESENTATION
            )
        )

        // Expect an exception because no error details is available from the mock.
        assertThrows<IllegalStateException> {
            backingStore.session(notaryRepIdentity) { session ->
                session.getTransactionDetails(List(1) { SecureHashUtils.randomSecureHash() })
            }
        }
    }

    @Test
    fun `Retrieve correct failed status without exceptions when both tx details and rejection details are present`() {
        val txId = SecureHashUtils.randomSecureHash()
        // Prepare a rejected transaction
        txnDetails.add(
            UniquenessTransactionDetailEntity(
                "SHA-256",
                txId.bytes,
                originatorX500Name,
                LocalDate.parse("2099-12-12").atStartOfDay().toInstant(ZoneOffset.UTC),
                LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC),
                UniquenessConstants.RESULT_REJECTED_REPRESENTATION
            )
        )

        errorEntities.add(
            UniquenessRejectedTransactionEntity(
                "SHA-256",
                txId.bytes,
                jpaBackingStoreObjectMapper().writeValueAsBytes(
                    UniquenessCheckErrorMalformedRequestImpl("Error")
                )
            )
        )

        backingStore.session(notaryRepIdentity) { session ->
            val txResult = session.getTransactionDetails(listOf(txId))[txId]?.result!!

            assertThat(txResult).isInstanceOf(UniquenessCheckResultFailure::class.java)
            assertThat((txResult as UniquenessCheckResultFailure).error)
                .isInstanceOf(UniquenessCheckErrorMalformedRequest::class.java)
        }
    }

    @Test
    fun `Executing transaction does not retry upon fatal exception`() {
        whenever(persistenceExceptionCategorizer.categorize(any())).thenReturn(PersistenceExceptionType.FATAL)
        var execCounter = 0
        assertThrows<DummyException> {
            backingStore.session(notaryRepIdentity) { session ->
                session.executeTransaction { _, _ ->
                    execCounter++
                    throw DummyException()
                }
            }
        }
        assertThat(execCounter).isEqualTo(1)
    }

    @Test
    fun `Executing transaction retries upon data_related exception`() {
        whenever(persistenceExceptionCategorizer.categorize(any())).thenReturn(PersistenceExceptionType.DATA_RELATED)
        var execCounter = 0
        assertThrows<IllegalStateException> {
            backingStore.session(notaryRepIdentity) { session ->
                session.executeTransaction { _, _ ->
                    execCounter++
                    throw DummyException()
                }
            }
        }
        assertThat(execCounter).isEqualTo(MAX_ATTEMPTS)
    }

    @Test
    fun `Executing transaction retries upon transient exception`() {
        whenever(persistenceExceptionCategorizer.categorize(any())).thenReturn(PersistenceExceptionType.TRANSIENT)
        var execCounter = 0
        assertThrows<IllegalStateException> {
            backingStore.session(notaryRepIdentity) { session ->
                session.executeTransaction { _, _ ->
                    execCounter++
                    throw DummyException()
                }
            }
        }
        assertThat(execCounter).isEqualTo(MAX_ATTEMPTS)
    }

    @Test
    fun `Executing transaction does not retry upon uncategorized exception`() {
        whenever(persistenceExceptionCategorizer.categorize(any())).thenReturn(PersistenceExceptionType.UNCATEGORIZED)
        var execCounter = 0
        assertThrows<DummyException> {
            backingStore.session(notaryRepIdentity) { session ->
                session.executeTransaction { _, _ ->
                    execCounter++
                    throw DummyException()
                }
            }
        }
        assertThat(execCounter).isEqualTo(1)
    }

    @Test
    fun `Executing transaction succeeds after transient failures`() {
        whenever(persistenceExceptionCategorizer.categorize(any())).thenReturn(PersistenceExceptionType.TRANSIENT)
        val retryCnt = 3
        var execCounter = 0
        assertDoesNotThrow {
            backingStore.session(notaryRepIdentity) { session ->
                session.executeTransaction { _, _ ->
                    execCounter++
                    if (execCounter < retryCnt)
                        throw OptimisticLockException()
                }
            }
        }
        assertThat(execCounter).isEqualTo(retryCnt)
    }

    class DummyException(message: String = "") : Exception(message)
}