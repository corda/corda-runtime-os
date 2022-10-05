package net.corda.uniqueness.backingstore.impl

import net.corda.crypto.testkit.SecureHashUtils
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.testkit.DbUtils
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.impl.JpaEntitiesRegistryImpl
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.uniqueness.backingstore.jpa.datamodel.JPABackingStoreEntities
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessTransactionDetailEntity
import net.corda.uniqueness.datamodel.common.UniquenessConstants
import net.corda.uniqueness.datamodel.common.toCharacterRepresentation
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorGeneralImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateRefImpl
import net.corda.uniqueness.datamodel.internal.UniquenessCheckRequestInternal
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.kotlin.times
import org.mockito.kotlin.never
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.persistence.EntityExistsException
import javax.persistence.EntityManagerFactory
import javax.persistence.OptimisticLockException
import javax.persistence.PersistenceException
import javax.persistence.QueryTimeoutException
import javax.persistence.RollbackException
import kotlin.reflect.full.createInstance
import kotlin.test.assertEquals

/**
 * Hint: To run tests against PostgreSQL, follow the steps in the link below.
 * https://github.com/corda/corda-runtime-os/wiki/Debugging-integration-tests#debugging-integration-tests-with-postgres
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JPABackingStoreImplIntegrationTests {
    private lateinit var backingStoreImpl: JPABackingStoreImpl
    private lateinit var lifecycleCoordinator: LifecycleCoordinator
    private lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory

    companion object {
        private val UPPER_BOUND = LocalDate.of(2200, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
        private val TEST_IDENTITY = createTestHoldingIdentity("C=GB, L=London, O=Alice", "Test Group").toAvro()
        private val MAX_ATTEMPTS = 10 // Set it same as "MAX_ATTEMPTS" in JPABackingStoreImpl.kt
        private val DB_NAME = "uniqueness_default"
        private val dbConfig = DbUtils.getEntityManagerConfiguration(DB_NAME)
        private val entityManagerFactory: EntityManagerFactory = EntityManagerFactoryFactoryImpl().create(
            DB_NAME, JPABackingStoreEntities.classes.toList(), dbConfig
        )
    }

    class DummyException(message: String) : Exception(message)

    @BeforeEach
    fun init() {
        lifecycleCoordinator = mock<LifecycleCoordinator>()
        lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>().apply {
            whenever(createCoordinator(any(), any())) doReturn lifecycleCoordinator
        }

        backingStoreImpl = createBackingStoreImpl(entityManagerFactory)
        backingStoreImpl.eventHandler(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mock())
    }

    private fun createBackingStoreImpl(emFactory: EntityManagerFactory): JPABackingStoreImpl {
        return JPABackingStoreImpl(lifecycleCoordinatorFactory,
            JpaEntitiesRegistryImpl(),
            mock<DbConnectionManager>().apply {
                whenever(getOrCreateEntityManagerFactory(any(), any(), any())) doReturn emFactory
                whenever(getClusterDataSource()) doReturn dbConfig.dataSource
            })
    }

    private fun createEntityManagerFactory(persistenceUnitName: String): EntityManagerFactory {
        return EntityManagerFactoryFactoryImpl().create(
            persistenceUnitName, JPABackingStoreEntities.classes.toList(), dbConfig
        )
    }

    private fun generateExternalRequest(txId: SecureHash): UniquenessCheckRequestAvro {
        val inputStateRef = "${SecureHashUtils.randomSecureHash()}:0"

        return UniquenessCheckRequestAvro.newBuilder(
            UniquenessCheckRequestAvro(
                TEST_IDENTITY,
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
                UPPER_BOUND
            )
        ).setInputStates(listOf(inputStateRef)).build()
    }

    @Nested
    inner class ExecutingTransactionRetryTests {
        @Test
        fun `Executing transaction retries upon expected exceptions`() {
            var execCounter = 0
            assertThrows<IllegalStateException> {
                backingStoreImpl.session { session ->
                    session.executeTransaction { _, _ ->
                        execCounter++
                        throw EntityExistsException()
                    }
                }
            }
            assertThat(execCounter).isEqualTo(MAX_ATTEMPTS)
        }

        @Test
        fun `Executing transaction does not retry upon unexpected exception`() {
            var execCounter = 0
            assertThrows<DummyException> {
                backingStoreImpl.session { session ->
                    session.executeTransaction { _, _ ->
                        execCounter++
                        throw DummyException("dummy exception")
                    }
                }
            }
            assertThat(execCounter).isEqualTo(1)
        }

        @Test
        fun `Executing transaction succeeds after transient failures`() {
            var execCounter = 0
            assertDoesNotThrow {
                backingStoreImpl.session { session ->
                    session.executeTransaction { _, _ ->
                        execCounter++
                        if (execCounter < 3)
                            throw OptimisticLockException()
                    }
                }
            }
            assertThat(execCounter).isEqualTo(3)
        }
    }

    @Nested
    inner class PersistingDataTests {
        @Test
        fun `Persisting accepted transaction details succeeds`() {
            val txId = SecureHashUtils.randomSecureHash()
            val txIds = listOf(txId)

            val externalRequest = generateExternalRequest(txId)
            val internalRequest = UniquenessCheckRequestInternal.create(externalRequest)
            val txns = listOf<Pair<UniquenessCheckRequestInternal, UniquenessCheckResult>>(
                Pair(internalRequest, UniquenessCheckResultSuccessImpl(Clock.systemUTC().instant()))
            )

            backingStoreImpl.session { session ->
                session.executeTransaction { _, txnOps -> txnOps.commitTransactions(txns) }
            }

            backingStoreImpl.session { session ->
                val result = session.getTransactionDetails(txIds)
                assertEquals(1, result.size)
                result.firstNotNullOf { secureHashTxnDetails ->
                    assertAll(
                        { assertThat(secureHashTxnDetails.key in txIds.toSet()) },
                        { assertThat(secureHashTxnDetails.value.result.toCharacterRepresentation()).isEqualTo(
                                UniquenessConstants.RESULT_ACCEPTED_REPRESENTATION) })
                }
            }
        }

        @Test
        fun `Persisting rejected transaction details succeeds`() {
            val txId = SecureHashUtils.randomSecureHash()
            val txIds = listOf(txId)
            val externalRequest = generateExternalRequest(txId)
            val internalRequest = UniquenessCheckRequestInternal.create(externalRequest)
            val txns = listOf(
                Pair(internalRequest, UniquenessCheckResultFailureImpl(
                        Clock.systemUTC().instant(), UniquenessCheckErrorGeneralImpl("some error")))
            )

            backingStoreImpl.session { session ->
                session.executeTransaction { _, txnOps -> txnOps.commitTransactions(txns) }
            }

            backingStoreImpl.session { session ->
                val result = session.getTransactionDetails(txIds)
                assertEquals(1, result.size)
                result.forEach { secureHashTxnDetails ->
                    assertAll(
                        { assertThat(secureHashTxnDetails.key in txIds.toSet()) },
                        { assertThat(secureHashTxnDetails.value.result.toCharacterRepresentation()).isEqualTo(
                                UniquenessConstants.RESULT_REJECTED_REPRESENTATION) })
                }
            }
        }

        @Test
        fun `Persisting unconsumed states succeeds`() {
            val hashCnt = 3
            val secureHashes = List(hashCnt) { SecureHashUtils.randomSecureHash() }
            val stateRefs = secureHashes.map { UniquenessCheckStateRefImpl(it, 0) }

            backingStoreImpl.session { session ->
                session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
            }

            backingStoreImpl.session { session ->
                val result = session.getStateDetails(stateRefs).toList()
                assertThat(result.size).isEqualTo(hashCnt)
                result.forEach { stateRefAndStateDetail ->
                    assertThat(secureHashes).contains(stateRefAndStateDetail.first.txHash)
                    assertThat(stateRefAndStateDetail.second.consumingTxId).isNull()
                }
            }
        }

        @Test
        fun `Consuming an unconsumed state succeeds`() {
            val hashCnt = 2
            val secureHashes = List(hashCnt) { SecureHashUtils.randomSecureHash() }
            val stateRefs = secureHashes.map { UniquenessCheckStateRefImpl(it, 0) }

            // Generate unconsumed states in DB.
            backingStoreImpl.session { session ->
                session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
            }

            // Consume one of unconsumed states in DB.
            val consumingTxId: SecureHash = SecureHashUtils.randomSecureHash()
            val consumingStateRef = stateRefs[0] // Consume the first out of two items.
            backingStoreImpl.session { session ->
                session.executeTransaction { _, txnOps ->
                    txnOps.consumeStates(consumingTxId = consumingTxId, stateRefs = listOf(consumingStateRef))
                }
            }

            // Verify if the target state has been correctly updated.
            backingStoreImpl.session { session ->
                val stateDetails = session.getStateDetails(stateRefs)
                val consumedStates = stateDetails.filterValues { it.consumingTxId != null }
                val unconsumedStates = stateDetails.filterValues { it.consumingTxId == null }
                assertAll(
                    { assertThat(consumedStates.count()).isEqualTo(1) },
                    { assertThat(consumedStates[consumingStateRef]?.consumingTxId).isEqualTo(consumingTxId) },
                    { assertThat(unconsumedStates.count()).isEqualTo(1) },
                    { assertThat(stateRefs[1].txHash).isEqualTo(unconsumedStates[stateRefs[1]]!!.stateRef.txHash) }
                )
            }
        }

        @Test
        fun `Double spend is prevented in separate sessions`() {
            val secureHashes = listOf(SecureHashUtils.randomSecureHash())
            val stateRefs = secureHashes.map { UniquenessCheckStateRefImpl(it, 0) }

            // Generate an unconsumed state in DB.
            backingStoreImpl.session { session ->
                session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
            }

            val consumingTxId = SecureHashUtils.randomSecureHash()
            val consumingStateRefs = listOf<UniquenessCheckStateRef>(stateRefs[0])

            backingStoreImpl.session { session ->
                session.executeTransaction { _, txnOps -> txnOps.consumeStates(consumingTxId, consumingStateRefs) }
            }

            // An attempt to spend an already spent state should fail.
            assertThrows<IllegalStateException> {
                backingStoreImpl.session { session ->
                    session.executeTransaction { _, txnOps -> txnOps.consumeStates(consumingTxId, consumingStateRefs) }
                }
            }
        }

        @Test
        fun `Double spend is prevented in one session`() {
            val secureHashes = listOf(SecureHashUtils.randomSecureHash())
            val stateRefs = secureHashes.map { UniquenessCheckStateRefImpl(it, 0) }

            // Generate an unconsumed state in DB.
            backingStoreImpl.session { session ->
                session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
            }

            val consumingTxId = SecureHashUtils.randomSecureHash()
            val consumingStateRefs = listOf<UniquenessCheckStateRef>(stateRefs[0])

            assertThrows<IllegalStateException> {
                backingStoreImpl.session { session ->
                    session.executeTransaction { _, txnOps ->
                        // Attempt a double-spend.
                        txnOps.consumeStates(consumingTxId, consumingStateRefs)
                        txnOps.consumeStates(consumingTxId, consumingStateRefs)
                    }
                }
            }
        }

        @Test
        fun `Attempt to consume an unknown state fails with an exception`() {
            val hashCnt = 2
            val secureHashes = List(hashCnt) { SecureHashUtils.randomSecureHash() }
            val stateRefs = secureHashes.map { UniquenessCheckStateRefImpl(it, 0) }

            // Generate unconsumed states in DB.
            backingStoreImpl.session { session ->
                session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
            }

            val consumingTxId: SecureHash = SecureHashUtils.randomSecureHash()
            val consumingStateRefs = listOf<UniquenessCheckStateRef>(UniquenessCheckStateRefImpl(consumingTxId, 0))

            assertThrows<IllegalStateException> {
                backingStoreImpl.session { session ->
                    session.executeTransaction { _, txnOps ->
                        txnOps.consumeStates(consumingTxId = consumingTxId, stateRefs = consumingStateRefs)
                    }
                }
            }
        }
    }

    @Nested
    inner class FlakyConnectionTests {
        @Disabled(
            "Re-iterate the test after reviewing the expected behaviour." +
                    "This test fails because QueryTimeoutException is not caught nor retried when querying."
        )
        @Test
        fun `Query timeout while querying triggers retry`() {
            val emFactory = createEntityManagerFactory("uniqueness")
            val spyEmFactory = Mockito.spy(emFactory)
            val em = spyEmFactory.createEntityManager()
            val spyEm = Mockito.spy(em)
            Mockito.doReturn(spyEm).whenever(spyEmFactory).createEntityManager()

            val queryName = "UniquenessTransactionDetailEntity.select"
            val resultClass = UniquenessTransactionDetailEntity::class.java
            // Actual execution of the query happens at invoking resultList of the query.
            // Find a way to mock a TypedQuery while make the logic JPA implementation agnostic (e.g. Hibernate).
            Mockito.doThrow(QueryTimeoutException("Executing a query timed out"))
                .whenever(spyEm).createNamedQuery(queryName, resultClass)

            val storeImpl = createBackingStoreImpl(spyEmFactory)
            storeImpl.eventHandler(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mock())

            assertThrows<QueryTimeoutException> {
                storeImpl.session { session ->
                    val txIds = listOf(SecureHashUtils.randomSecureHash())
                    session.getTransactionDetails(txIds)
                }
            }
            Mockito.verify(spyEm, times(MAX_ATTEMPTS)).createNamedQuery(queryName, resultClass)
        }

        @Disabled(
            "Re-iterate the test after reviewing the expected behaviour." +
                    "This test fails because PersistenceException is not caught nor retried while persisting."
        )
        @ParameterizedTest
        @ValueSource(
            classes = [PersistenceException::class,
                EntityExistsException::class,
                RollbackException::class,
                OptimisticLockException::class]
        )
        fun `Persistence errors raised while persisting trigger retry`(e: Class<Exception>) {
            val emFactory = createEntityManagerFactory("uniqueness")
            val spyEmFactory = Mockito.spy(emFactory)
            val em = spyEmFactory.createEntityManager()
            val spyEm = Mockito.spy(em)
            Mockito.doThrow(e.kotlin.createInstance()).whenever(spyEm).persist(any())
            Mockito.doReturn(spyEm).whenever(spyEmFactory).createEntityManager()

            val storeImpl = createBackingStoreImpl(spyEmFactory)
            storeImpl.eventHandler(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mock())

            val secureHashes = listOf(SecureHashUtils.randomSecureHash())
            val stateRefs = secureHashes.map { UniquenessCheckStateRefImpl(it, 0) }
            assertThrows<IllegalStateException> {
                storeImpl.session { session ->
                    session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
                }
            }
            Mockito.verify(spyEm, times(MAX_ATTEMPTS)).persist(any())
        }

        @Test
        fun `Transaction rollback gets triggered if transaction is active for an unexpected exception type`() {
            val emFactory = createEntityManagerFactory("uniqueness")
            val spyEmFactory = Mockito.spy(emFactory)
            val em = emFactory.createEntityManager()
            val spyEm = Mockito.spy(em)
            val emTransaction = em.transaction
            val spyEmTransaction = Mockito.spy(emTransaction)
            Mockito.doReturn(spyEm).whenever(spyEmFactory).createEntityManager()
            Mockito.doReturn(spyEmTransaction).whenever(spyEm).transaction

            val storeImpl = createBackingStoreImpl(spyEmFactory)
            storeImpl.eventHandler(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mock())

            assertThrows<DummyException> {
                storeImpl.session { session ->
                    session.executeTransaction { _, _ -> throw DummyException("dummy exception") }
                }
            }
            // Note that unlike for expected exceptions, no retry should happen.
            Mockito.verify(spyEmTransaction, times(1)).begin()
            Mockito.verify(spyEmTransaction, never()).commit()
            Mockito.verify(spyEmTransaction, times(1)).rollback()
        }
    }

    @Test
    fun `Session always creates a new entity manager`() {
        val emFactory = createEntityManagerFactory("uniqueness")
        val spyEmFactory = Mockito.spy(emFactory)

        val storeImpl = createBackingStoreImpl(spyEmFactory)
        storeImpl.eventHandler(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mock())

        val sessionInvokeCnt = 3
        repeat(sessionInvokeCnt) {
            storeImpl.session { session -> session.executeTransaction { _, _ -> } }
        }
        Mockito.verify(spyEmFactory, times(sessionInvokeCnt)).createEntityManager()
    }
}