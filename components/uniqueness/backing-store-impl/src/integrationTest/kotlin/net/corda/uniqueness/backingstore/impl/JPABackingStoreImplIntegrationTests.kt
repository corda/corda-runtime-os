package net.corda.uniqueness.backingstore.impl

import net.corda.crypto.testkit.SecureHashUtils
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.db.testkit.DatabaseInstaller
import net.corda.db.testkit.DbUtils
import net.corda.db.testkit.TestDbInfo
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.impl.JpaEntitiesRegistryImpl
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.uniqueness.backingstore.jpa.datamodel.JPABackingStoreEntities
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessTransactionDetailEntity
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorMalformedRequestImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorInputStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorTimeWindowOutOfBoundsImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateRefImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateDetailsImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorInputStateConflictImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateConflictImpl
import net.corda.uniqueness.datamodel.internal.UniquenessCheckRequestInternal
import net.corda.uniqueness.datamodel.internal.UniquenessCheckTransactionDetailsInternal
import net.corda.uniqueness.utils.UniquenessAssertions
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateDetails
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.never
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.Clock
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityExistsException
import javax.persistence.RollbackException
import javax.persistence.OptimisticLockException
import kotlin.reflect.full.createInstance

/**
 * Note: To run tests against PostgreSQL, follow the steps in the link below.
 * https://github.com/corda/corda-runtime-os/wiki/Debugging-integration-tests#debugging-integration-tests-with-postgres
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JPABackingStoreImplIntegrationTests {
    private lateinit var backingStoreImpl: JPABackingStoreImpl
    private lateinit var lifecycleCoordinator: LifecycleCoordinator
    private lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory

    companion object {
        private val DEFAULT_TIME_WINDOW_UPPER_BOUND = LocalDate.of(2200, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
        private const val MAX_ATTEMPTS = 10

        private val aliceIdentity = createTestHoldingIdentity("C=GB, L=London, O=Alice", "Test Group")
        private val aliceIdentityDbName = VirtualNodeDbType.UNIQUENESS.getSchemaName(aliceIdentity.shortHash)
        private val dbConfig = DbUtils.getEntityManagerConfiguration(aliceIdentityDbName)
        private val databaseInstaller = DatabaseInstaller(
            EntityManagerFactoryFactoryImpl(),
            LiquibaseSchemaMigratorImpl(),
            JpaEntitiesRegistryImpl()
        )
        private val aliceEmFactory: EntityManagerFactory = databaseInstaller.setupDatabase(
            TestDbInfo("uniq_test_default", aliceIdentityDbName),
            "vnode-uniqueness",
            JPABackingStoreEntities.classes
        )
        private val noDbEmFactory: EntityManagerFactory = EntityManagerFactoryFactoryImpl()
            .create("test", JPABackingStoreEntities.classes.toList(), dbConfig)
    }

    class DummyException(message: String) : Exception(message)

    private fun generateRequestInternal(txId: SecureHash = SecureHashUtils.randomSecureHash()) =
        UniquenessCheckRequestInternal(
            txId,
            txId.toString(),
            emptyList(),
            emptyList(),
            0,
            null,
            DEFAULT_TIME_WINDOW_UPPER_BOUND
        )

    @BeforeEach
    fun init() {
        lifecycleCoordinator = mock<LifecycleCoordinator>()
        lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>().apply {
            whenever(createCoordinator(any(), any())) doReturn lifecycleCoordinator
        }

        backingStoreImpl = createBackingStoreImpl(aliceEmFactory)
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

    @Nested
    inner class ExecutingTransactionRetryTests {
        @Test
        fun `Executing transaction retries upon expected exceptions`() {
            var execCounter = 0
            assertThrows<IllegalStateException> {
                backingStoreImpl.session(aliceIdentity) { session ->
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
                backingStoreImpl.session(aliceIdentity) { session ->
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
            val retryCnt = 3
            var execCounter = 0
            assertDoesNotThrow {
                backingStoreImpl.session(aliceIdentity) { session ->
                    session.executeTransaction { _, _ ->
                        execCounter++
                        if (execCounter < retryCnt)
                            throw OptimisticLockException()
                    }
                }
            }
            assertThat(execCounter).isEqualTo(retryCnt)
        }
    }

    @Nested
    inner class PersistingDataTests {
        @Test
        fun `Persisting accepted transaction details succeeds`() {
            val txId = SecureHashUtils.randomSecureHash()
            val txIds = listOf(txId)
            val txns = listOf<Pair<UniquenessCheckRequestInternal, UniquenessCheckResult>>(
                Pair(generateRequestInternal(txId), UniquenessCheckResultSuccessImpl(Clock.systemUTC().instant()))
            )

            backingStoreImpl.session(aliceIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.commitTransactions(txns) }
            }

            lateinit var txnDetails: Map<SecureHash, UniquenessCheckTransactionDetailsInternal>
            backingStoreImpl.session(aliceIdentity) { session ->
                txnDetails = session.getTransactionDetails(txIds)
            }

            assertThat(txnDetails.size).isEqualTo(1)
            assertThat(txIds).contains(txnDetails.entries.single().key)
            UniquenessAssertions.assertAcceptedResult<UniquenessCheckResultSuccess>(
                txnDetails.entries.single().value.result)
        }

        @Test
        fun `Persisting rejected transaction due to input state unknown error succeeds`() {
            val txId = SecureHashUtils.randomSecureHash()
            val txIds = listOf(txId)
            val txns = listOf(
                Pair(generateRequestInternal(txId), UniquenessCheckResultFailureImpl(
                        Clock.systemUTC().instant(),
                        UniquenessCheckErrorInputStateUnknownImpl(listOf(UniquenessCheckStateRefImpl(txId, 0)))))
            )

            backingStoreImpl.session(aliceIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.commitTransactions(txns) }
            }

            lateinit var txnDetails: Map<SecureHash, UniquenessCheckTransactionDetailsInternal>
            backingStoreImpl.session(aliceIdentity) { session ->
                txnDetails = session.getTransactionDetails(txIds)
            }

            assertThat(txnDetails.size).isEqualTo(1)
            assertThat(txIds).contains(txnDetails.entries.single().key)
            UniquenessAssertions.assertInputStateUnknownResult(txId, txnDetails.entries.single().value.result)
        }

        @Test
        fun `Persisting rejected transaction due to input state conflict succeeds`() {
            val txId = SecureHashUtils.randomSecureHash()
            val txIds = listOf(txId)
            val consumingTxId = SecureHashUtils.randomSecureHash()
            val txns = listOf(Pair(generateRequestInternal(txId), UniquenessCheckResultFailureImpl(
                        Clock.systemUTC().instant(),
                        UniquenessCheckErrorInputStateConflictImpl(
                            listOf(UniquenessCheckStateDetailsImpl(
                                    UniquenessCheckStateRefImpl(txId, 0), consumingTxId = consumingTxId))))))

            backingStoreImpl.session(aliceIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.commitTransactions(txns) }
            }

            lateinit var txnDetails: Map<SecureHash, UniquenessCheckTransactionDetailsInternal>
            backingStoreImpl.session(aliceIdentity) { session ->
                txnDetails = session.getTransactionDetails(txIds)
            }

            assertThat(txnDetails.size).isEqualTo(1)
            assertThat(txIds).contains(txnDetails.entries.single().key)
            UniquenessAssertions.assertInputStateConflictResult(
                txId, consumingTxId, txnDetails.entries.single().value.result
            )
        }

        @Test
        fun `Persisting rejected transaction due to reference state conflict succeeds`() {
            val txId = SecureHashUtils.randomSecureHash()
            val txIds = listOf(txId)
            val consumingTxId = SecureHashUtils.randomSecureHash()
            val txns = listOf(
                Pair(generateRequestInternal(txId), UniquenessCheckResultFailureImpl(
                        Clock.systemUTC().instant(),
                        UniquenessCheckErrorReferenceStateConflictImpl(
                            listOf(UniquenessCheckStateDetailsImpl(
                                    UniquenessCheckStateRefImpl(txId, 0), consumingTxId = consumingTxId))))))

            backingStoreImpl.session(aliceIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.commitTransactions(txns) }
            }

            lateinit var txnDetails: Map<SecureHash, UniquenessCheckTransactionDetailsInternal>
            backingStoreImpl.session(aliceIdentity) { session ->
                txnDetails = session.getTransactionDetails(txIds)
            }

            assertThat(txnDetails.size).isEqualTo(1)
            assertThat(txIds).contains(txnDetails.entries.single().key)
            UniquenessAssertions.assertReferenceStateConflictResult(
                txId, consumingTxId, txnDetails.entries.single().value.result)
        }

        @Test
        fun `Persisting rejected transaction due to reference state unknown succeeds`() {
            val txId = SecureHashUtils.randomSecureHash()
            val txIds = listOf(txId)
            val txns = listOf(
                Pair(generateRequestInternal(txId), UniquenessCheckResultFailureImpl(
                    Clock.systemUTC().instant(),
                    UniquenessCheckErrorReferenceStateUnknownImpl(
                            listOf(UniquenessCheckStateRefImpl(txId, 0))))))

            backingStoreImpl.session(aliceIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.commitTransactions(txns) }
            }

            lateinit var txnDetails: Map<SecureHash, UniquenessCheckTransactionDetailsInternal>
            backingStoreImpl.session(aliceIdentity) { session ->
                txnDetails = session.getTransactionDetails(txIds)
            }

            assertThat(txnDetails.size).isEqualTo(1)
            assertThat(txIds).contains(txnDetails.entries.single().key)
            UniquenessAssertions.assertReferenceStateUnknownResult(txId, txnDetails.entries.single().value.result)
        }

        @Test
        fun `Persisting rejected transaction due to time window out of bound succeeds`() {
            val txId = SecureHashUtils.randomSecureHash()
            val txIds = listOf(txId)

            val lowerBound: Instant = LocalDateTime.of(2022, 9, 30, 0, 0).toInstant(ZoneOffset.UTC)
            val upperBound: Instant = LocalDateTime.of(2022, 10, 2, 0, 0).toInstant(ZoneOffset.UTC)
            val evaluationTime: Instant = LocalDateTime.of(2022, 10, 3, 0, 0).toInstant(ZoneOffset.UTC)
            val txns = listOf(Pair(generateRequestInternal(txId), UniquenessCheckResultFailureImpl(
                        Clock.systemUTC().instant(),
                        UniquenessCheckErrorTimeWindowOutOfBoundsImpl(evaluationTime, lowerBound, upperBound))))

            backingStoreImpl.session(aliceIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.commitTransactions(txns) }
            }

            lateinit var txnDetails: Map<SecureHash, UniquenessCheckTransactionDetailsInternal>
            backingStoreImpl.session(aliceIdentity) { session ->
                txnDetails = session.getTransactionDetails(txIds)
            }

            assertThat(txnDetails.size).isEqualTo(1)
            assertThat(txIds).contains(txnDetails.entries.single().key)
            UniquenessAssertions.assertTimeWindowOutOfBoundsResult(
                evaluationTime, lowerBound, upperBound, txnDetails.entries.single().value.result)
        }

        @Disabled("This test fails because it fails to persist an error message with 1024 bytes.")
        @Test
        fun `Persisting an error throws if the size is bigger than the maximum`() {
            val txId = SecureHashUtils.randomSecureHash()
            val internalRequest = generateRequestInternal(txId)

            // 1024 is the expected maximum size of an error message.
            val maxErrMsgLength = 1024
            val validErrorMessage = "e".repeat(maxErrMsgLength)
            assertDoesNotThrow {
                backingStoreImpl.session(aliceIdentity) { session ->
                    session.executeTransaction { _, txnOps -> txnOps.commitTransactions(
                        listOf(Pair(internalRequest, UniquenessCheckResultFailureImpl(
                                Clock.systemUTC().instant(),
                                UniquenessCheckErrorMalformedRequestImpl(validErrorMessage))))) }
                }
            }

            // Persisting this error should throw because its size if bigger than 1024.
            val invalidErrorMessage = "e".repeat(maxErrMsgLength + 1)
            assertThrows<IllegalStateException> {
                backingStoreImpl.session(aliceIdentity) { session ->
                    session.executeTransaction { _, txnOps -> txnOps.commitTransactions(
                        listOf(Pair(internalRequest, UniquenessCheckResultFailureImpl(
                                Clock.systemUTC().instant(),
                                UniquenessCheckErrorMalformedRequestImpl(invalidErrorMessage))))) }
                }
            }
        }

        @Test
        fun `Persisting rejected transaction with general error succeeds`() {
            val txId = SecureHashUtils.randomSecureHash()
            val txIds = listOf(txId)
            val errorMessage = "some error message"
            val txns = listOf(
                Pair(generateRequestInternal(txId), UniquenessCheckResultFailureImpl(
                        Clock.systemUTC().instant(), UniquenessCheckErrorMalformedRequestImpl(errorMessage))))

            backingStoreImpl.session(aliceIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.commitTransactions(txns) }
            }

            lateinit var txnDetails: Map<SecureHash, UniquenessCheckTransactionDetailsInternal>
            backingStoreImpl.session(aliceIdentity) { session ->
                txnDetails = session.getTransactionDetails(txIds)
            }

            assertThat(txnDetails.size).isEqualTo(1)
            assertThat(txIds).contains(txnDetails.entries.single().key)
            UniquenessAssertions.assertMalformedRequestResult(errorMessage, txnDetails.entries.single().value.result)
        }

        @Test
        fun `Persisting unconsumed states succeeds`() {
            val hashCnt = 3
            val secureHashes = List(hashCnt) { SecureHashUtils.randomSecureHash() }
            val stateRefs = secureHashes.map { UniquenessCheckStateRefImpl(it, 0) }

            backingStoreImpl.session(aliceIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
            }

            lateinit var stateDetails: Map<UniquenessCheckStateRef, UniquenessCheckStateDetails>
            backingStoreImpl.session(aliceIdentity) { session ->
                stateDetails = session.getStateDetails(stateRefs)
            }

            assertThat(stateDetails.size).isEqualTo(hashCnt)
            stateDetails.forEach { (stateRef, stateDetail) ->
                assertAll(
                    { assertThat(secureHashes).contains(stateRef.txHash) },
                    { assertThat(stateDetail.consumingTxId).isNull()} )
            }
        }

        @Test
        fun `Consuming an unconsumed state succeeds`() {
            val stateRefs = List(2) { UniquenessCheckStateRefImpl(SecureHashUtils.randomSecureHash(), 0) }

            // Generate unconsumed states in DB.
            backingStoreImpl.session(aliceIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
            }

            // Consume one of unconsumed states in DB.
            val consumingTxId: SecureHash = SecureHashUtils.randomSecureHash()
            val consumingStateRef = stateRefs[0] // Consume the first out of two items.
            backingStoreImpl.session(aliceIdentity) { session ->
                session.executeTransaction { _, txnOps ->
                    txnOps.consumeStates(consumingTxId = consumingTxId, stateRefs = listOf(consumingStateRef))
                }
            }

            // Verify if the target state has been correctly updated.
            lateinit var stateDetails: Map<UniquenessCheckStateRef, UniquenessCheckStateDetails>
            backingStoreImpl.session(aliceIdentity) { session ->
                stateDetails = session.getStateDetails(stateRefs)
            }

            stateDetails.values.partition { it.consumingTxId == null }.let { (unconsumedStates, consumedStates) ->
                assertAll(
                    { assertThat(unconsumedStates.count()).isEqualTo(1) },
                    { assertThat(unconsumedStates.single().stateRef.txHash).isEqualTo(stateRefs[1].txHash) },
                    { assertThat(unconsumedStates.single().consumingTxId).isNull() },
                    { assertThat(consumedStates.count()).isEqualTo(1) },
                    { assertThat(consumedStates.single().stateRef.txHash).isEqualTo(stateRefs[0].txHash) },
                    { assertThat(consumedStates.single().consumingTxId).isEqualTo(consumingTxId) }
                )
            }
        }

        @Test
        fun `Double spend is prevented in separate sessions`() {
            val stateRefs = List(1) { UniquenessCheckStateRefImpl(SecureHashUtils.randomSecureHash(), 0) }

            // Generate an unconsumed state in DB.
            backingStoreImpl.session(aliceIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
            }

            val consumingTxId = SecureHashUtils.randomSecureHash()
            val consumingStateRefs = listOf<UniquenessCheckStateRef>(stateRefs[0])

            assertDoesNotThrow {
                backingStoreImpl.session(aliceIdentity) { session ->
                    session.executeTransaction { _, txnOps -> txnOps.consumeStates(consumingTxId, consumingStateRefs) }
                }
            }

            // An attempt to spend an already spent state should fail.
            assertThrows<IllegalStateException> {
                backingStoreImpl.session(aliceIdentity) { session ->
                    session.executeTransaction { _, txnOps -> txnOps.consumeStates(consumingTxId, consumingStateRefs) }
                }
            }
        }

        @Test
        fun `Double spend is prevented in one session`() {
            val stateRefs = List(1) { UniquenessCheckStateRefImpl(SecureHashUtils.randomSecureHash(), 0) }

            // Generate an unconsumed state in DB.
            backingStoreImpl.session(aliceIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
            }

            val consumingStateRefs = listOf<UniquenessCheckStateRef>(stateRefs[0])
            assertThrows<IllegalStateException> {
                backingStoreImpl.session(aliceIdentity) { session ->
                    session.executeTransaction { _, txnOps ->
                        // Attempt a double-spend.
                        txnOps.consumeStates(SecureHashUtils.randomSecureHash(), consumingStateRefs)
                        txnOps.consumeStates(SecureHashUtils.randomSecureHash(), consumingStateRefs)
                    }
                }
            }
        }

        @Test
        fun `Attempt to consume an unknown state fails with an exception`() {
            val stateRefs = List(2) { UniquenessCheckStateRefImpl(SecureHashUtils.randomSecureHash(), 0) }

            // Generate unconsumed states in DB.
            backingStoreImpl.session(aliceIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
            }

            val consumingTxId: SecureHash = SecureHashUtils.randomSecureHash()
            val consumingStateRefs = listOf<UniquenessCheckStateRef>(UniquenessCheckStateRefImpl(consumingTxId, 0))

            assertThrows<IllegalStateException> {
                backingStoreImpl.session(aliceIdentity) { session ->
                    session.executeTransaction { _, txnOps ->
                        txnOps.consumeStates(consumingTxId = consumingTxId, stateRefs = consumingStateRefs)
                    }
                }
            }
        }
    }

    @Nested
    inner class FlakyConnectionTests {
        // Review with CORE-4983 for different types of exceptions such as QueryTimeoutException.
        @Test
        fun `Exceptions thrown while querying triggers retry`() {
            val emFactory = createEntityManagerFactory("uniqueness")
            val spyEmFactory = Mockito.spy(emFactory)
            val em = spyEmFactory.createEntityManager()
            val spyEm = Mockito.spy(em)
            Mockito.doReturn(spyEm).whenever(spyEmFactory).createEntityManager()

            val queryName = "UniquenessTransactionDetailEntity.select"
            val resultClass = UniquenessTransactionDetailEntity::class.java
            // Actual execution of the query happens at invoking resultList of the query.
            // Find a way to mock a TypedQuery while make the logic JPA implementation agnostic (e.g. Hibernate).
            Mockito.doThrow(EntityExistsException()).whenever(spyEm).createNamedQuery(queryName, resultClass)

            val storeImpl = createBackingStoreImpl(spyEmFactory)
            storeImpl.eventHandler(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mock())

            assertThrows<EntityExistsException> {
                storeImpl.session(aliceIdentity) { session ->
                    val txIds = listOf(SecureHashUtils.randomSecureHash())
                    session.getTransactionDetails(txIds)
                }
            }
            Mockito.verify(spyEm, times(1)).createNamedQuery(queryName, resultClass)
        }

        // Review with CORE-4983 for different types of exceptions such as PersistenceException.
        @ParameterizedTest
        @ValueSource(classes = [EntityExistsException::class,
                                RollbackException::class,
                                OptimisticLockException::class]
        )
        fun `Persistence errors raised while persisting triggers retry`(e: Class<Exception>) {
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
                storeImpl.session(aliceIdentity) { session ->
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
                storeImpl.session(aliceIdentity) { session ->
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
            storeImpl.session(aliceIdentity) { session ->
                session.executeTransaction { _, _ -> } }
        }
        Mockito.verify(spyEmFactory, times(sessionInvokeCnt)).createEntityManager()
    }

    @Test
    fun `Persisting with an incorrect identity throws an expected exception`() {
        val storeImpl = createBackingStoreImpl(noDbEmFactory)
        storeImpl.eventHandler(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mock())

        try {
            val stateRefs = List(1) { UniquenessCheckStateRefImpl(SecureHashUtils.randomSecureHash(), 0) }
            storeImpl.session(aliceIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
            }
            Assertions.fail("The test failed if it reaches here. An exception must be thrown.")
        } catch (e: IllegalStateException) {
            // Expect a RollbackException at committing.
            assertThat(e.cause).isInstanceOf(RollbackException::class.java)
        }
    }
}