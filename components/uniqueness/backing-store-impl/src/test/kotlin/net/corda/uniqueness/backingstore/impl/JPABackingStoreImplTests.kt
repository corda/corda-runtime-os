package net.corda.uniqueness.backingstore.impl

import net.corda.crypto.testkit.SecureHashUtils
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.CloseableDataSource
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessRejectedTransactionEntity
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessStateDetailEntity
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessTransactionDetailEntity
import net.corda.uniqueness.datamodel.common.toCharacterRepresentation
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorGeneralImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateRefImpl
import net.corda.uniqueness.datamodel.internal.UniquenessCheckRequestInternal
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.toAvro
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import java.sql.Connection
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.LinkedList
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.TypedQuery
import javax.persistence.EntityExistsException
import javax.persistence.RollbackException
import javax.persistence.OptimisticLockException
import kotlin.test.assertEquals
import kotlin.test.assertFalse


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JPABackingStoreImplTests {
    private lateinit var backingStoreImpl: JPABackingStoreImpl

    private lateinit var lifecycleCoordinator: LifecycleCoordinator
    private lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var entityManager: EntityManager
    private lateinit var entityTransaction: EntityTransaction
    private lateinit var entityManagerFactory: EntityManagerFactory
    private lateinit var dummyDataSource: CloseableDataSource
    private lateinit var jpaEntitiesRegistry: JpaEntitiesRegistry

    private lateinit var txnDetails: LinkedList<UniquenessTransactionDetailEntity>
    private lateinit var txnDetailQuery: TypedQuery<UniquenessTransactionDetailEntity>
    private lateinit var stateEntities: List<UniquenessStateDetailEntity>
    private lateinit var stateDetailSelectQuery: TypedQuery<UniquenessStateDetailEntity>
    private lateinit var txnErrors: LinkedList<UniquenessRejectedTransactionEntity>
    private lateinit var txnErrorQuery: TypedQuery<UniquenessRejectedTransactionEntity>

    // NOTE: While expecting refactoring around createDefaultUniquenessDb(), it's mocked for testing
    //  convenience. Since it's a final class, MockMaker's been added under resources with content "mock-maker-inline".
    private lateinit var schemaMigrator: LiquibaseSchemaMigratorImpl
    private lateinit var dbConnectionManager: DbConnectionManager

    inner class DummyLifecycle : LifecycleEvent

    class DummyException(message: String) : Exception(message)

    @Suppress("ComplexMethod")
    @BeforeEach
    fun init() {
        lifecycleCoordinator = mock<LifecycleCoordinator>()
        lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>().apply {
            whenever(createCoordinator(any(), any())) doReturn lifecycleCoordinator
        }
        entityTransaction = mock<EntityTransaction>().apply {
            whenever(isActive) doReturn true
        }

        stateEntities = mock<List<UniquenessStateDetailEntity>>()
        stateDetailSelectQuery = mock<TypedQuery<UniquenessStateDetailEntity>>().apply {
            whenever(setParameter(eq("txAlgo"), any())) doReturn this
            whenever(setParameter(eq("txId"), any())) doReturn this
            whenever(setParameter(eq("stateIndex"), any())) doReturn this
            whenever(resultList) doReturn stateEntities
        }

        txnDetails = mock<LinkedList<UniquenessTransactionDetailEntity>>()
        txnDetailQuery = mock<TypedQuery<UniquenessTransactionDetailEntity>>().apply {
            whenever(setParameter(eq("txAlgo"), any())) doReturn this
            whenever(setParameter(eq("txId"), any())) doReturn this
            whenever(resultList) doReturn txnDetails
        }

        txnErrors = mock<LinkedList<UniquenessRejectedTransactionEntity>>()
        txnErrorQuery = mock<TypedQuery<UniquenessRejectedTransactionEntity>>().apply {
            whenever(setParameter(eq("txAlgo"), any())) doReturn this
            whenever(setParameter(eq("txId"), any())) doReturn this
            whenever(resultList) doReturn txnErrors
        }

        entityManager = mock<EntityManager>().apply {
            whenever(transaction) doReturn entityTransaction
            whenever(
                createNamedQuery("UniquenessStateDetailEntity.select", UniquenessStateDetailEntity::class.java)
            ) doReturn stateDetailSelectQuery
            whenever(
                createNamedQuery(
                    "UniquenessTransactionDetailEntity.select",
                    UniquenessTransactionDetailEntity::class.java
                )
            ) doReturn txnDetailQuery
            whenever(
                createNamedQuery(
                    "UniquenessRejectedTransactionEntity.select",
                    UniquenessRejectedTransactionEntity::class.java
                )
            ) doReturn txnErrorQuery
        }

        entityManagerFactory = mock<EntityManagerFactory>().apply {
            whenever(createEntityManager()) doReturn entityManager
        }

        dummyDataSource = mock<CloseableDataSource>().apply {
            whenever(connection) doReturn mock<Connection>()
        }

        jpaEntitiesRegistry = mock<JpaEntitiesRegistry>().apply {
            whenever(get(any())) doReturn mock<JpaEntitiesSet>()
        }

        schemaMigrator = mock<LiquibaseSchemaMigratorImpl>()
        dbConnectionManager = mock<DbConnectionManager>().apply {
            whenever(getClusterDataSource()) doReturn dummyDataSource
            whenever(getOrCreateEntityManagerFactory(any(), any(), any())) doReturn entityManagerFactory
        }

        backingStoreImpl = JPABackingStoreImpl(
            lifecycleCoordinatorFactory,
            jpaEntitiesRegistry,
            dbConnectionManager,
            schemaMigrator = schemaMigrator
        )
    }

    private fun generateSecureHashes(cnt: Int): LinkedList<SecureHash> {
        val secureHashes = LinkedList<SecureHash>()
        repeat(cnt) {
            secureHashes.push(SecureHashUtils.randomSecureHash())
        }
        return secureHashes
    }

    private fun generateUniquenessCheckStateRef(hashes: LinkedList<SecureHash>): LinkedList<UniquenessCheckStateRef> {
        val uniquenessCheckInternalStateRefs = hashes.let {
            val tmpUniquenessCheckInternalStateRefs = LinkedList<UniquenessCheckStateRef>()
            it.forEachIndexed { i, hash ->
                tmpUniquenessCheckInternalStateRefs.add(UniquenessCheckStateRefImpl(hash, i))
            }
            tmpUniquenessCheckInternalStateRefs
        }

        return uniquenessCheckInternalStateRefs
    }

    private fun generateExternalRequest(txId: SecureHash): UniquenessCheckRequestAvro {
        val inputStateRef = "${SecureHashUtils.randomSecureHash()}:0"

        return UniquenessCheckRequestAvro.newBuilder(
            UniquenessCheckRequestAvro(
                createTestHoldingIdentity("C=GB, L=London, O=Alice", "Test Group").toAvro(),
                ExternalEventContext(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    KeyValuePairList(emptyList())
                ),
                txId.toString(),
                emptyList(),
                emptyList(),
                0,
                null,
                LocalDate.of(2200, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
            )
        ).setInputStates(listOf(inputStateRef)).build()
    }

    @Nested
    inner class LifeCycleTests {
        @Test
        fun `Starting backing store invokes life cycle start`() {
            backingStoreImpl.start()
            Mockito.verify(lifecycleCoordinator).start()
        }

        @Test
        fun `Stopping backing store invokes life cycle stop`() {
            backingStoreImpl.stop()
            Mockito.verify(lifecycleCoordinator).stop()
        }

        @Test
        fun `Get running life cycle status`() {
            backingStoreImpl.isRunning
            Mockito.verify(lifecycleCoordinator).isRunning
        }
    }

    @Nested
    inner class EventHandlerTests {
        @Test
        fun `StartEvent register and start all`() {
            val mockCoordinator = mock<LifecycleCoordinator>()
            backingStoreImpl.eventHandler(StartEvent(), mockCoordinator)

            Mockito.verify(mockCoordinator).followStatusChangesByName(
                eq(
                    setOf(LifecycleCoordinatorName.forComponent<DbConnectionManager>())
                )
            )
            // REVIEW: review the expected behaviour and update test accordingly
//            assertTrue(backingStoreImpl.isRunning)
//            Mockito.verify(lifecycleCoordinator).updateStatus(LifecycleStatus.UP)
        }

        @Test
        fun `StopEvent stops all`() {
            val mockCoordinator = mock<LifecycleCoordinator>()
            backingStoreImpl.eventHandler(StopEvent(), mockCoordinator)

            assertFalse(backingStoreImpl.isRunning)
            // REVIEW: review the expected behaviour and update test accordingly
//            Mockito.verify(lifecycleCoordinator).updateStatus(LifecycleStatus.DOWN)
        }

        @Test
        fun `Registration status change event instantiates entity manager when event status is up`() {
            val mockCoordinator = mock<LifecycleCoordinator>()
            backingStoreImpl.eventHandler(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mockCoordinator)
            Mockito.verify(dbConnectionManager).getOrCreateEntityManagerFactory(any(), any(), any())
        }

        @Test
        fun `Registration status change event does not instantiate entity manager if event status is not up`() {
            val mockCoordinator = mock<LifecycleCoordinator>()
            backingStoreImpl.eventHandler(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), mockCoordinator)
            Mockito.verify(mockCoordinator).updateStatus(LifecycleStatus.DOWN)
            Mockito.verify(dbConnectionManager, never()).getOrCreateEntityManagerFactory(any(), any(), any())

            backingStoreImpl.eventHandler(RegistrationStatusChangeEvent(mock(), LifecycleStatus.ERROR), mockCoordinator)
            Mockito.verify(mockCoordinator).updateStatus(LifecycleStatus.ERROR)
            Mockito.verify(dbConnectionManager, never()).getOrCreateEntityManagerFactory(any(), any(), any())

            backingStoreImpl.eventHandler(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mockCoordinator)
            Mockito.verify(mockCoordinator).updateStatus(LifecycleStatus.UP)
            Mockito.verify(dbConnectionManager, times(1)).getOrCreateEntityManagerFactory(any(), any(), any())
        }

        @Test
        fun `Unknown life cycle event does not throw exception`() {
            assertDoesNotThrow {
                backingStoreImpl.eventHandler(DummyLifecycle(), mock())
            }
        }
    }

    @Nested
    inner class ClosingSessionBlockTests {
        @BeforeEach
        fun init() {
            backingStoreImpl.eventHandler(
                RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), lifecycleCoordinator
            )
        }

        @Test
        fun `Session always closes entity manager after use`() {
            backingStoreImpl.session { }
            Mockito.verify(entityManager, times(1)).close()
        }

        @Test
        fun `Session closes entity manager even when exception occurs`() {
            assertThrows<java.lang.RuntimeException> {
                backingStoreImpl.session { throw java.lang.RuntimeException("test exception") }
            }
            Mockito.verify(entityManager, times(1)).close()
        }
    }

    @Nested
    inner class TransactionOpsTests {
        @BeforeEach
        fun init() {
            backingStoreImpl.eventHandler(
                RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), lifecycleCoordinator
            )
        }

        @Test
        fun `Creating unconsumed states persist correct fields`() {
            val hashCnt = 1
            val secureHashes = generateSecureHashes(hashCnt)
            val stateRefs = generateUniquenessCheckStateRef(secureHashes)

            backingStoreImpl.session { session ->
                session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
            }

            Mockito.verify(entityManager, times(hashCnt)).persist(
                UniquenessStateDetailEntity(
                    stateRefs[0].txHash.algorithm,
                    stateRefs[0].txHash.bytes,
                    stateRefs[0].stateIndex.toLong(),
                    null,
                    null
                )
            )
        }

        @Test
        fun `Commiting a failed transaction persists error data`() {
            backingStoreImpl.session { session ->
                val txId = SecureHashUtils.randomSecureHash()
                val txns = LinkedList<Pair<UniquenessCheckRequestInternal, UniquenessCheckResult>>()
                val externalRequest = generateExternalRequest(txId)
                val internalRequest = UniquenessCheckRequestInternal.create(externalRequest)
                txns.add(
                    Pair(
                        internalRequest,
                        UniquenessCheckResultFailureImpl(
                            Clock.systemUTC().instant(), UniquenessCheckErrorGeneralImpl("some error")
                        )
                    )
                )

                session.executeTransaction { _, txnOps ->
                    txnOps.commitTransactions(txns)

                    Mockito.verify(entityManager, Mockito.atMostOnce()).persist(
                        UniquenessStateDetailEntity(
                            txns[0].first.txId.algorithm,
                            txns[0].first.txId.bytes,
                            txns[0].first.inputStates[0].stateIndex.toLong(),
                            null,
                            null
                        )
                    )
                    Mockito.verify(entityManager, Mockito.atMostOnce()).persist(
                        UniquenessRejectedTransactionEntity(
                            txns[0].first.txId.algorithm,
                            txns[0].first.txId.bytes,
                            jpaBackingStoreObjectMapper().writeValueAsBytes(txns[0].second)
                        )
                    )
                }
            }
        }
    }


    @Nested
    inner class TransactionTests {
        private val maxRetriesCnt = 10
        private val expectedTxnExceptions = mapOf(
            "EntityExistsException" to EntityExistsException(),
            "RollbackException" to RollbackException(),
            "OptimisticLockException" to OptimisticLockException()
        )

        @BeforeEach
        fun init() {
            backingStoreImpl.eventHandler(
                RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), lifecycleCoordinator
            )
        }

        @Test
        fun `Executing transaction runs with transaction begin and commit`() {
            backingStoreImpl.session { session ->
                session.executeTransaction { _, _ -> }
            }

            Mockito.verify(entityTransaction, times(1)).begin()
            Mockito.verify(entityTransaction, times(1)).commit()
            Mockito.verify(entityManager, times(1)).close()
        }

        @ParameterizedTest
        @ValueSource(strings = ["EntityExistsException", "RollbackException", "OptimisticLockException"])
        fun `Executing transaction retries upon expected exceptions`(exception: String) {
            assertThrows<IllegalStateException> {
                backingStoreImpl.session { session ->
                    session.executeTransaction { _, _ -> throw expectedTxnExceptions[exception]!! }
                }
            }

            Mockito.verify(entityTransaction, times(maxRetriesCnt)).begin()
            Mockito.verify(entityTransaction, never()).commit()
        }

        @Test
        fun `Executing transaction does not retry upon unexpected exception`() {
            assertThrows<DummyException> {
                backingStoreImpl.session { session ->
                    session.executeTransaction { _, _ -> throw DummyException("dummy exception") }
                }
            }
            Mockito.verify(entityTransaction, times(1)).begin()
            Mockito.verify(entityTransaction, never()).commit()
        }

        @Test
        fun `Executing transaction triggers rollback upon receiving expected exception if transaction is active`() {
            assertThrows<java.lang.IllegalStateException> {
                backingStoreImpl.session { session ->
                    session.executeTransaction { _, _ -> throw EntityExistsException() }
                }
            }
            Mockito.verify(entityTransaction, times(maxRetriesCnt)).rollback()
        }

        @Test
        fun `Getting state details invokes correct query`() {
            val hashCnt = 3
            val secureHashes = generateSecureHashes(hashCnt)
            val stateRefs = generateUniquenessCheckStateRef(secureHashes)

            backingStoreImpl.session { session ->
                session.getStateDetails(stateRefs)
                Mockito.verify(entityManager, times(hashCnt))
                    .createNamedQuery(
                        eq("UniquenessStateDetailEntity.select"),
                        eq(UniquenessStateDetailEntity::class.java)
                    )
            }
        }

        @Test
        fun `Getting transaction details invokes correct query for a successful result`() {
            val txIds = LinkedList<SecureHash>()
            txIds.add(SecureHashUtils.randomSecureHash())

            txnDetails.apply {
                whenever(firstOrNull()) doReturn UniquenessTransactionDetailEntity(
                    "SHA-256",
                    "0xA1".toByteArray(),
                    LocalDate.parse("2099-12-12").atStartOfDay().toInstant(ZoneOffset.UTC),
                    LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC),
                    'A'
                )
            }

            backingStoreImpl.session { session ->
                val result = session.getTransactionDetails(txIds)
                assertEquals(1, result.size)
                assertEquals(txIds[0], result[txIds[0]]!!.txId)
                assertEquals('A', result[txIds[0]]!!.result.toCharacterRepresentation())
                Mockito.verify(entityManager)
                    .createNamedQuery(
                        eq("UniquenessTransactionDetailEntity.select"),
                        eq(UniquenessTransactionDetailEntity::class.java)
                    )
            }
        }

        @Test
        fun `Throw if no error detail is available for a failed transaction`() {
            // Prepare a rejected transaction
            txnDetails.apply {
                whenever(firstOrNull()) doReturn UniquenessTransactionDetailEntity(
                    "SHA-256",
                    "0xA1".toByteArray(),
                    LocalDate.parse("2099-12-12").atStartOfDay().toInstant(ZoneOffset.UTC),
                    LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC),
                    'R'
                )
            }

            // Expect an exception because no error details is available from the mock.
            assertThrows<IllegalStateException> {
                val txIds = LinkedList<SecureHash>()
                txIds.add(SecureHashUtils.randomSecureHash())

                backingStoreImpl.session { session ->
                    session.getTransactionDetails(txIds)
                }
            }
        }

        @Test
        fun `Getting transaction details invokes correct query for a failed result`() {
            // Prepare a rejected transaction
            txnDetails.apply {
                whenever(firstOrNull()) doReturn UniquenessTransactionDetailEntity(
                    "SHA-256",
                    "0xA1".toByteArray(),
                    LocalDate.parse("2099-12-12").atStartOfDay().toInstant(ZoneOffset.UTC),
                    LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC),
                    'R'
                )
            }
            // Prepare a transaction error
            txnErrors.apply {
                whenever(firstOrNull()) doReturn UniquenessRejectedTransactionEntity(
                    "SHA-256",
                    "0xA1".toByteArray(),
                    "{\"type\": \"generalErrorImpl\", \"errorText\": \"test error\"}".toByteArray()
                )
            }

            backingStoreImpl.session { session ->
                val txIds = LinkedList<SecureHash>()
                txIds.add(SecureHashUtils.randomSecureHash())
                val result = session.getTransactionDetails(txIds)

                assertEquals(1, result.size)
                assertEquals(txIds[0], result[txIds[0]]!!.txId)
                assertEquals('R', result[txIds[0]]!!.result.toCharacterRepresentation())
                Mockito.verify(entityManager)
                    .createNamedQuery(
                        eq("UniquenessTransactionDetailEntity.select"),
                        eq(UniquenessTransactionDetailEntity::class.java)
                    )
                Mockito.verify(entityManager)
                    .createNamedQuery(
                        eq("UniquenessRejectedTransactionEntity.select"),
                        eq(UniquenessRejectedTransactionEntity::class.java)
                    )
            }
        }

        @Test
        fun `Getting transaction details invokes correct query for an invalid result`() {
            val txIds = LinkedList<SecureHash>()
            txIds.add(SecureHashUtils.randomSecureHash())

            txnDetails.apply {
                whenever(firstOrNull()) doReturn UniquenessTransactionDetailEntity(
                    "SHA-256",
                    "0xA1".toByteArray(),
                    LocalDate.parse("2099-12-12").atStartOfDay().toInstant(ZoneOffset.UTC),
                    LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC),
                    'X' // Invalid result.
                )
            }

            // Expect an IllegalStateException for an invalid result type.
            assertThrows<IllegalStateException> {
                backingStoreImpl.session { session ->
                    session.getTransactionDetails(txIds)

                    Mockito.verify(entityManager)
                        .createNamedQuery(
                            eq("UniquenessTransactionDetailEntity.select"),
                            eq(UniquenessTransactionDetailEntity::class.java)
                        )
                    Mockito.verify(entityManager)
                        .createNamedQuery(
                            eq("UniquenessRejectedTransactionEntity.select"),
                            eq(UniquenessRejectedTransactionEntity::class.java)
                        )
                }
            }
        }
    }
}