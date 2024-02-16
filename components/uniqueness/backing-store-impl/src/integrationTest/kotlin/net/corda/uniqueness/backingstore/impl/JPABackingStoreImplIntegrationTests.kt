package net.corda.uniqueness.backingstore.impl

import net.corda.crypto.testkit.SecureHashUtils
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.db.testkit.DatabaseInstaller
import net.corda.db.testkit.DbUtils
import net.corda.db.testkit.TestDbInfo
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.impl.JpaEntitiesRegistryImpl
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.AutoTickTestClock
import net.corda.uniqueness.datamodel.common.UniquenessConstants
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorInputStateConflictImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorInputStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorMalformedRequestImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateConflictImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorTimeWindowOutOfBoundsImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateDetailsImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateRefImpl
import net.corda.uniqueness.datamodel.internal.UniquenessCheckRequestInternal
import net.corda.uniqueness.datamodel.internal.UniquenessCheckTransactionDetailsInternal
import net.corda.uniqueness.utils.UniquenessAssertions
import net.corda.utilities.rootCause
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateDetails
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.VirtualNodeInfo
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.Session
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import javax.persistence.EntityExistsException
import javax.persistence.EntityManagerFactory
import javax.persistence.OptimisticLockException
import javax.persistence.RollbackException
import kotlin.reflect.full.createInstance

/**
 * Note: To run tests against PostgreSQL, follow the steps in the link below.
 * https://github.com/corda/corda-runtime-os/wiki/Debugging-integration-tests#debugging-integration-tests-with-postgres
 *
 * Also, in order to run the Intellij Code Coverage feature, you may need to exclude the following types to avoid
 * Hibernate related errors.
 *  - org.hibernate.hql.internal.antlr.HqlTokenTypes
 *  - org.hibernate.hql.internal.antlr.SqlTokenTypes
 *  - org.hibernate.sql.ordering.antlr.GeneratedOrderByFragmentRendererTokenTypes
 */
@Suppress("FunctionName")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JPABackingStoreImplIntegrationTests {
    private lateinit var backingStoreImpl: JPABackingStoreImpl
    private lateinit var testClock: AutoTickTestClock
    private val baseTime = Instant.EPOCH
    private val defaultTimeWindowUpperBound = LocalDate.of(2200, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC)

    private val groupId = UUID.randomUUID().toString()
    private val notaryVNodeIdentity = createTestHoldingIdentity("C=GB, L=London, O=NotaryRep1", groupId)
    private val notaryVNodeIdentityDbName = VirtualNodeDbType.UNIQUENESS.getSchemaName(notaryVNodeIdentity.shortHash)
    private val notaryVNodeIdentityDbId = UUID.randomUUID()
    private val dbConfig = DbUtils.getEntityManagerConfiguration(notaryVNodeIdentityDbName)
    private val databaseInstaller = DatabaseInstaller(
        EntityManagerFactoryFactoryImpl(),
        LiquibaseSchemaMigratorImpl(),
        JpaEntitiesRegistryImpl()
    )
    private val notaryVNodeEmFactory: EntityManagerFactory = databaseInstaller.setupDatabase(
        TestDbInfo(name = "unique_test_default", schemaName = notaryVNodeIdentityDbName, rewriteBatchedInserts = true),
        "vnode-uniqueness",
        JPABackingStoreEntities.classes
    )

    private val originatorX500Name = "C=GB, L=London, O=Alice"

    companion object {
        private const val MAX_ATTEMPTS = 10
    }

    class DummyException(message: String) : Exception(message)

    private fun generateRequestInternal(txId: SecureHash = SecureHashUtils.randomSecureHash()) =
        UniquenessCheckRequestInternal(
            txId,
            txId.toString(),
            originatorX500Name,
            emptyList(),
            emptyList(),
            0,
            null,
            defaultTimeWindowUpperBound
        )

    @BeforeEach
    fun init() {
        backingStoreImpl = createBackingStoreImpl(notaryVNodeEmFactory)

        testClock = AutoTickTestClock(baseTime, Duration.ofSeconds(1))
    }

    private fun createBackingStoreImpl(emFactory: EntityManagerFactory): JPABackingStoreImpl {
        val jpaEntitiesRegistry = JpaEntitiesRegistryImpl()
        val clusterEntityManagerFactory = mock<EntityManagerFactory>().apply {
            whenever(createEntityManager()).thenReturn(mock())
        }
        val dbConnectionManager = mock<DbConnectionManager>().apply {
            whenever(getOrCreateEntityManagerFactory(eq(notaryVNodeIdentityDbId), any(), any())) doReturn emFactory
            whenever(getClusterDataSource()) doReturn dbConfig.dataSource
            whenever(getClusterEntityManagerFactory()) doReturn clusterEntityManagerFactory
        }
        val virtualNodeRepository: VirtualNodeRepository = mock<VirtualNodeRepository>().apply {
            whenever(find(any(), any())) doReturn VirtualNodeInfo(
                holdingIdentity = notaryVNodeIdentity,
                CpiIdentifier("", "", SecureHashUtils.randomSecureHash()),
                vaultDmlConnectionId = UUID.randomUUID(),
                cryptoDmlConnectionId = UUID.randomUUID(),
                uniquenessDmlConnectionId = notaryVNodeIdentityDbId,
                timestamp = Instant.EPOCH
            )
        }
        return JPABackingStoreImpl(jpaEntitiesRegistry, dbConnectionManager, virtualNodeRepository)
    }

    private fun createEntityManagerFactory(persistenceUnitName: String = "uniqueness"): EntityManagerFactory {
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
                backingStoreImpl.session(notaryVNodeIdentity) { session ->
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
                backingStoreImpl.session(notaryVNodeIdentity) { session ->
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
                backingStoreImpl.session(notaryVNodeIdentity) { session ->
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
                Pair(generateRequestInternal(txId), UniquenessCheckResultSuccessImpl(testClock.instant()))
            )

            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.commitTransactions(txns) }
            }

            val txnDetails = mutableMapOf<SecureHash, UniquenessCheckTransactionDetailsInternal>()
            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                txnDetails.putAll(session.getTransactionDetails(txIds))
            }

            UniquenessAssertions.assertContainingTxId(txnDetails, txIds.single())
            UniquenessAssertions.assertAcceptedResult(txnDetails.entries.single().value.result, testClock)
        }

        @Test
        fun `Persisting rejected transaction due to input state unknown error succeeds`() {
            val txId = SecureHashUtils.randomSecureHash()
            val txIds = listOf(txId)
            val stateIdx = 0
            val txns = listOf(
                Pair(
                    generateRequestInternal(txId), UniquenessCheckResultFailureImpl(
                        testClock.instant(),
                        UniquenessCheckErrorInputStateUnknownImpl(listOf(UniquenessCheckStateRefImpl(txId, stateIdx)))
                    )
                )
            )

            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.commitTransactions(txns) }
            }

            val txnDetails = mutableMapOf<SecureHash, UniquenessCheckTransactionDetailsInternal>()
            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                txnDetails.putAll(session.getTransactionDetails(txIds))
            }

            UniquenessAssertions.assertContainingTxId(txnDetails, txIds.single())
            UniquenessAssertions.assertInputStateUnknownResult(
                txnDetails.entries.single().value.result,
                txId,
                stateIdx,
                testClock
            )
        }

        @Test
        fun `Persisting rejected transaction due to input state conflict succeeds`() {
            val txId = SecureHashUtils.randomSecureHash()
            val txIds = listOf(txId)
            val consumingTxId = SecureHashUtils.randomSecureHash()
            val txns = listOf(
                Pair(
                    generateRequestInternal(txId), UniquenessCheckResultFailureImpl(
                        testClock.instant(), UniquenessCheckErrorInputStateConflictImpl(
                            listOf(
                                UniquenessCheckStateDetailsImpl(
                                    UniquenessCheckStateRefImpl(txId, 0), consumingTxId
                                )
                            )
                        )
                    )
                )
            )

            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.commitTransactions(txns) }
            }

            val txnDetails = mutableMapOf<SecureHash, UniquenessCheckTransactionDetailsInternal>()
            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                txnDetails.putAll(session.getTransactionDetails(txIds))
            }

            UniquenessAssertions.assertContainingTxId(txnDetails, txIds.single())
            UniquenessAssertions.assertInputStateConflictResult(
                txnDetails.entries.single().value.result, txId, consumingTxId, 0, testClock
            )
        }

        @Test
        // Temporary test for tactical fix delivered in CORE-18025. Should be removed / replaced when
        // CORE-17155 (strategic fix) is implemented.
        fun `Persisting rejected transaction with multiple input state conflicts only stores first failure`() {
            val txId = SecureHashUtils.randomSecureHash()
            val txIds = listOf(txId)
            val consumingTxId = SecureHashUtils.randomSecureHash()
            val txns = listOf(
                Pair(
                    generateRequestInternal(txId), UniquenessCheckResultFailureImpl(
                        testClock.instant(), UniquenessCheckErrorInputStateConflictImpl(
                            List(2) {
                                UniquenessCheckStateDetailsImpl(
                                    UniquenessCheckStateRefImpl(txId, it), consumingTxId
                                )
                            }
                        )
                    )
                )
            )

            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.commitTransactions(txns) }
            }

            val txnDetails = mutableMapOf<SecureHash, UniquenessCheckTransactionDetailsInternal>()
            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                txnDetails.putAll(session.getTransactionDetails(txIds))
            }

            UniquenessAssertions.assertContainingTxId(txnDetails, txIds.single())
            UniquenessAssertions.assertInputStateConflictResult(
                txnDetails.entries.single().value.result, txId, consumingTxId, 0, testClock
            )
        }

        @Test
        fun `Persisting rejected transaction due to reference state conflict succeeds`() {
            val txId = SecureHashUtils.randomSecureHash()
            val txIds = listOf(txId)
            val consumingTxId = SecureHashUtils.randomSecureHash()
            val txns = listOf(
                Pair(
                    generateRequestInternal(txId), UniquenessCheckResultFailureImpl(
                        testClock.instant(),
                        UniquenessCheckErrorReferenceStateConflictImpl(
                            listOf(
                                UniquenessCheckStateDetailsImpl(
                                    UniquenessCheckStateRefImpl(txId, 0), consumingTxId
                                )
                            )
                        )
                    )
                )
            )

            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.commitTransactions(txns) }
            }

            val txnDetails = mutableMapOf<SecureHash, UniquenessCheckTransactionDetailsInternal>()
            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                txnDetails.putAll(session.getTransactionDetails(txIds))
            }

            UniquenessAssertions.assertContainingTxId(txnDetails, txIds.single())
            UniquenessAssertions.assertReferenceStateConflictResult(
                txnDetails.entries.single().value.result, txId, consumingTxId, 0, testClock
            )
        }

        @Test
        fun `Persisting rejected transaction due to reference state unknown succeeds`() {
            val txId = SecureHashUtils.randomSecureHash()
            val txIds = listOf(txId)
            val txns = listOf(
                Pair(
                    generateRequestInternal(txId), UniquenessCheckResultFailureImpl(
                        testClock.instant(),
                        UniquenessCheckErrorReferenceStateUnknownImpl(
                            listOf(UniquenessCheckStateRefImpl(txId, 0))
                        )
                    )
                )
            )

            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.commitTransactions(txns) }
            }

            val txnDetails = mutableMapOf<SecureHash, UniquenessCheckTransactionDetailsInternal>()
            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                txnDetails.putAll(session.getTransactionDetails(txIds))
            }

            UniquenessAssertions.assertContainingTxId(txnDetails, txIds.single())
            UniquenessAssertions.assertReferenceStateUnknownResult(
                txnDetails.entries.single().value.result,
                txId,
                0,
                testClock
            )
        }

        @Test
        fun `Persisting rejected transaction due to time window out of bound succeeds`() {
            val txId = SecureHashUtils.randomSecureHash()
            val txIds = listOf(txId)

            val lowerBound: Instant = LocalDateTime.of(2022, 9, 30, 0, 0).toInstant(ZoneOffset.UTC)
            val upperBound: Instant = LocalDateTime.of(2022, 10, 2, 0, 0).toInstant(ZoneOffset.UTC)
            val evaluationTime: Instant = LocalDateTime.of(2022, 10, 3, 0, 0).toInstant(ZoneOffset.UTC)
            val txns = listOf(
                Pair(
                    generateRequestInternal(txId), UniquenessCheckResultFailureImpl(
                        testClock.instant(), UniquenessCheckErrorTimeWindowOutOfBoundsImpl(
                            evaluationTime,
                            lowerBound,
                            upperBound)
                    )
                )
            )

            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.commitTransactions(txns) }
            }

            val txnDetails = mutableMapOf<SecureHash, UniquenessCheckTransactionDetailsInternal>()
            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                txnDetails.putAll(session.getTransactionDetails(txIds))
            }

            UniquenessAssertions.assertContainingTxId(txnDetails, txIds.single())
            UniquenessAssertions.assertTimeWindowOutOfBoundsResult(
                txnDetails.entries.single().value.result, evaluationTime, lowerBound, upperBound, testClock
            )
        }

        @Test
        fun `Persisting an error throws if the size is bigger than the maximum`() {
            // We need to establish the object size without any message (i.e. a blank message) to
            // see how much space we need to fill in order to hit our maximum valid  size.
            val baseObjectSize = jpaBackingStoreObjectMapper()
                .writeValueAsBytes(UniquenessCheckErrorMalformedRequestImpl("")).size

            // Available characters that need filling is the hard-coded limit minus fixed size
            val maxErrMsgLength =
                UniquenessConstants.REJECTED_TRANSACTION_ERROR_DETAILS_LENGTH - baseObjectSize

            val validErrorMessage = "e".repeat(maxErrMsgLength)
            assertDoesNotThrow {
                backingStoreImpl.session(notaryVNodeIdentity) { session ->
                    session.executeTransaction { _, txnOps ->
                        txnOps.commitTransactions(
                            listOf(
                                Pair(
                                    generateRequestInternal(SecureHashUtils.randomSecureHash()),
                                    UniquenessCheckResultFailureImpl(
                                        testClock.instant(),
                                        UniquenessCheckErrorMalformedRequestImpl(validErrorMessage)
                                    )
                                )
                            )
                        )
                    }
                }
            }

            // Persisting this error should throw because its size if bigger than 1024.
            val invalidErrorMessage = "e".repeat(maxErrMsgLength + 1)
            assertThrows<IllegalArgumentException> {
                backingStoreImpl.session(notaryVNodeIdentity) { session ->
                    session.executeTransaction { _, txnOps ->
                        txnOps.commitTransactions(
                            listOf(
                                Pair(
                                    generateRequestInternal(SecureHashUtils.randomSecureHash()),
                                    UniquenessCheckResultFailureImpl(
                                        testClock.instant(),
                                        UniquenessCheckErrorMalformedRequestImpl(invalidErrorMessage)
                                    )
                                )
                            )
                        )
                    }
                }
            }
        }

        @Test
        fun `Persisting rejected transaction with general error succeeds`() {
            val txId = SecureHashUtils.randomSecureHash()
            val txIds = listOf(txId)
            val errorMessage = "some error message"
            val txns = listOf(
                Pair(
                    generateRequestInternal(txId), UniquenessCheckResultFailureImpl(
                        testClock.instant(), UniquenessCheckErrorMalformedRequestImpl(errorMessage)
                    )
                )
            )

            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.commitTransactions(txns) }
            }

            val txnDetails = mutableMapOf<SecureHash, UniquenessCheckTransactionDetailsInternal>()
            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                txnDetails.putAll(session.getTransactionDetails(txIds))
            }

            UniquenessAssertions.assertContainingTxId(txnDetails, txIds.single())
            UniquenessAssertions.assertMalformedRequestResult(
                txnDetails.entries.single().value.result,
                errorMessage,
                testClock)
        }

        @Test
        fun `Persisting unconsumed states succeeds`() {
            val secureHashes = List(3) { SecureHashUtils.randomSecureHash() }
            val stateRefs = secureHashes.map { UniquenessCheckStateRefImpl(it, 0) }

            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
            }

            val stateDetails = mutableMapOf<UniquenessCheckStateRef, UniquenessCheckStateDetails>()
            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                stateDetails.putAll(session.getStateDetails(stateRefs))
            }

            assertThat(stateDetails.size).isEqualTo(secureHashes.size)
            stateDetails.forEach { (stateRef, stateDetail) ->
                assertAll(
                    { assertThat(secureHashes).contains(stateRef.txHash) },
                    { assertThat(stateDetail.consumingTxId).isNull() })
            }
        }

        @Test
        fun `Consuming an unconsumed state succeeds`() {
            val stateRefs = List(2) { UniquenessCheckStateRefImpl(SecureHashUtils.randomSecureHash(), 0) }

            // Generate unconsumed states in DB.
            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
            }

            // Consume one of unconsumed states in DB.
            val consumingTxId = SecureHashUtils.randomSecureHash()
            val consumingStateRef = stateRefs[0] // Consume the first out of two items.
            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                session.executeTransaction { _, txnOps ->
                    txnOps.consumeStates(consumingTxId, listOf(consumingStateRef))
                }
            }

            // Verify if the target state has been correctly updated.
            val stateDetails = mutableMapOf<UniquenessCheckStateRef, UniquenessCheckStateDetails>()
            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                stateDetails.putAll(session.getStateDetails(stateRefs))
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
            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
            }

            val consumingStateRefs = listOf<UniquenessCheckStateRef>(stateRefs[0])

            assertDoesNotThrow {
                backingStoreImpl.session(notaryVNodeIdentity) { session ->
                    session.executeTransaction { _, txnOps ->
                        txnOps.consumeStates(SecureHashUtils.randomSecureHash(), consumingStateRefs) }
                }
            }

            // An attempt to spend an already spent state should fail.
            assertThrows<IllegalStateException> {
                backingStoreImpl.session(notaryVNodeIdentity) { session ->
                    session.executeTransaction { _, txnOps ->
                        txnOps.consumeStates(SecureHashUtils.randomSecureHash(), consumingStateRefs) }
                }
            }
        }

        @Test
        fun `Double spend is prevented in one session`() {
            val stateRefs = List(1) { UniquenessCheckStateRefImpl(SecureHashUtils.randomSecureHash(), 0) }

            // Generate an unconsumed state in DB.
            backingStoreImpl.session(notaryVNodeIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
            }

            val consumingStateRefs = listOf<UniquenessCheckStateRef>(stateRefs[0])
            assertThrows<IllegalStateException> {
                backingStoreImpl.session(notaryVNodeIdentity) { session ->
                    session.executeTransaction { _, txnOps ->
                        // Attempt a double-spend.
                        txnOps.consumeStates(SecureHashUtils.randomSecureHash(), consumingStateRefs)
                        txnOps.consumeStates(SecureHashUtils.randomSecureHash(), consumingStateRefs)
                    }
                }
            }
        }

        // This test is repeated a number of times due to in-flight double spends being a timing
        // issue that can be difficult to reproduce reliably
        @RepeatedTest(10)
        fun `In-flight double spend is prevented using separate backing store instances executing in parallel`() {
            val numExecutors = 4
            val stateRefs = List(5) { UniquenessCheckStateRefImpl(SecureHashUtils.randomSecureHash(), it) }

            // Generate an unconsumed state in DB.
            backingStoreImpl.transactionSession(notaryVNodeIdentity) { _, txnOps ->
                txnOps.createUnconsumedStates(stateRefs)
            }

            // Use an additional backing store for each thread to guarantee no interference from
            // shared state etc.
            val additionalBackingStores = List(numExecutors) {
                createBackingStoreImpl(notaryVNodeEmFactory)
            }

            val spendTasks = List(numExecutors) {
                Callable {
                    additionalBackingStores[it].transactionSession(notaryVNodeIdentity) { _, txnOps ->
                        txnOps.consumeStates(SecureHashUtils.randomSecureHash(), stateRefs.shuffled())
                    }
                }
            }

            with (Executors.newFixedThreadPool(numExecutors)) {
                val exceptions = invokeAll(spendTasks).map { task ->
                    try {
                        task.get()
                        null
                    } catch (e: ExecutionException) {
                        e.rootCause
                    } finally {
                        shutdownNow()
                    }
                }

                // All but one should fail
                assertThat(exceptions)
                    .hasSize(numExecutors)
                    .containsOnlyOnce(null)
                assertThat(exceptions.filterNotNull().map { it.message })
                    .containsOnly("No states were consumed, this might be an in-flight double spend")
            }
        }

        @Test
        fun `Attempt to consume an unknown state fails with an exception`() {
            val consumingTxId = SecureHashUtils.randomSecureHash()
            val consumingStateRefs = listOf<UniquenessCheckStateRef>(UniquenessCheckStateRefImpl(consumingTxId, 0))

            assertThrows<IllegalStateException> {
                backingStoreImpl.session(notaryVNodeIdentity) { session ->
                    session.executeTransaction { _, txnOps ->
                        txnOps.consumeStates(consumingTxId, consumingStateRefs)
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
            val spyEmFactory = Mockito.spy(createEntityManagerFactory())
            val spyEm = Mockito.spy(spyEmFactory.createEntityManager())
            Mockito.doReturn(spyEm).whenever(spyEmFactory).createEntityManager()

            val mockSession = mock<Session> {
                on { byMultipleIds(eq(UniquenessTransactionDetailEntity::class.java)) } doThrow EntityExistsException()
            }
            Mockito.doReturn(spyEm).whenever(spyEmFactory).createEntityManager()
            Mockito.doReturn(mockSession).whenever(spyEm).unwrap(eq(Session::class.java))

            val storeImpl = createBackingStoreImpl(spyEmFactory)

            assertThrows<EntityExistsException> {
                storeImpl.session(notaryVNodeIdentity) { session ->
                    val txIds = listOf(SecureHashUtils.randomSecureHash())
                    session.getTransactionDetails(txIds)
                }
            }
        }

        // Review with CORE-4983 for different types of exceptions such as PersistenceException.
        @ParameterizedTest
        @ValueSource(classes = [EntityExistsException::class, RollbackException::class, OptimisticLockException::class])
        fun `Persistence errors raised while persisting triggers retry`(e: Class<Exception>) {
            val spyEmFactory = Mockito.spy(createEntityManagerFactory())
            val spyEm = Mockito.spy(spyEmFactory.createEntityManager())
            Mockito.doThrow(e.kotlin.createInstance()).whenever(spyEm).persist(any())
            Mockito.doReturn(spyEm).whenever(spyEmFactory).createEntityManager()

            val storeImpl = createBackingStoreImpl(spyEmFactory)

            val secureHashes = listOf(SecureHashUtils.randomSecureHash())
            val stateRefs = secureHashes.map { UniquenessCheckStateRefImpl(it, 0) }
            assertThrows<IllegalStateException> {
                storeImpl.session(notaryVNodeIdentity) { session ->
                    session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
                }
            }
            Mockito.verify(spyEm, times(MAX_ATTEMPTS)).persist(any())
        }

        @Test
        fun `Transaction rollback gets triggered if transaction is active for an unexpected exception type`() {
            val spyEmFactory = Mockito.spy(createEntityManagerFactory())
            val spyEm = Mockito.spy(spyEmFactory.createEntityManager())
            val spyEmTransaction = Mockito.spy(spyEm.transaction)
            Mockito.doReturn(spyEm).whenever(spyEmFactory).createEntityManager()
            Mockito.doReturn(spyEmTransaction).whenever(spyEm).transaction

            val storeImpl = createBackingStoreImpl(spyEmFactory)

            assertThrows<DummyException> {
                storeImpl.session(notaryVNodeIdentity) { session ->
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
        val spyEmFactory = Mockito.spy(createEntityManagerFactory())
        val storeImpl = createBackingStoreImpl(spyEmFactory)

        val sessionInvokeCnt = 3
        repeat(sessionInvokeCnt) {
            storeImpl.session(notaryVNodeIdentity) { session ->
                session.executeTransaction { _, _ -> }
            }
        }
        Mockito.verify(spyEmFactory, times(sessionInvokeCnt)).createEntityManager()
    }

    @Disabled(
        "Review with CORE-7201. While this test produces an error saying 'user lacks privilege or object not found'," +
                "the reason is not clearly understood thus it can be misleading. Knowing exactly what exception will be " +
                "thrown due to lack of privilege will be useful. But at the same time, reproducing it with the DB testing" +
                "framework is not straightforward, and such scenario is less likely to happen in real life. Therefore there " +
                "is a low risk and this shouldn't be a blocker to deliver other tests."
    )
    @Test
    fun `Persisting with an incorrect DB set up throws a rollback exception at committing`() {
        val noDbEmFactory: EntityManagerFactory = EntityManagerFactoryFactoryImpl()
            .create("testunit", JPABackingStoreEntities.classes.toList(), dbConfig)

        val storeImpl = createBackingStoreImpl(noDbEmFactory)

        try {
            val stateRefs = List(1) { UniquenessCheckStateRefImpl(SecureHashUtils.randomSecureHash(), 0) }
            storeImpl.session(notaryVNodeIdentity) { session ->
                session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
            }
            Assertions.fail("The test failed if it reaches here. An exception must be thrown.")
        } catch (e: IllegalStateException) {
            // Expect a RollbackException at committing.
            assertThat(e.cause).isInstanceOf(RollbackException::class.java)
        }
    }
}
