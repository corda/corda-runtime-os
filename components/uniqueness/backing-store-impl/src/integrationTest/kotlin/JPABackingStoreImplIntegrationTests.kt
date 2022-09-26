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
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.kotlin.times
import org.mockito.Mockito
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.LinkedList
import javax.persistence.EntityExistsException
import javax.persistence.EntityManagerFactory
import javax.persistence.OptimisticLockException
import javax.persistence.PersistenceException
import javax.persistence.QueryTimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Hint: To run tests against PostgreSQL, follow the steps in the link below.
 * https://github.com/corda/corda-runtime-os/wiki/Debugging-integration-tests#debugging-integration-tests-with-postgres
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JPABackingStoreImplIntegrationTests {
    private val maxAttemptsCnt = 10 // Set it same as "MAX_ATTEMPTS" in JPABackingStoreImpl.kt
    private lateinit var backingStoreImpl: JPABackingStoreImpl

    private val entityManagerFactory: EntityManagerFactory
    private val dbConfig = DbUtils.getEntityManagerConfiguration("uniqueness_default")

    private lateinit var lifecycleCoordinator: LifecycleCoordinator
    private lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory

    class DummyException(message: String) : Exception(message)

    init {
        entityManagerFactory = createEntityManagerFactory("uniqueness_default")
    }

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
            assertEquals(maxAttemptsCnt, execCounter)
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
            assertEquals(1, execCounter)
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
            assertEquals(3, execCounter)
        }
    }

    @Nested
    inner class PersistingDataTests {
        @Test
        fun `Persisting accepted transaction details succeeds`() {
            val txCnt = 1
            val txns = LinkedList<Pair<UniquenessCheckRequestInternal, UniquenessCheckResult>>()
            val txIds = LinkedList<SecureHash>()

            repeat(txCnt) {
                val txId = SecureHashUtils.randomSecureHash()
                val externalRequest = generateExternalRequest(txId)
                txIds.add(txId)

                val internalRequest = UniquenessCheckRequestInternal.create(externalRequest)
                txns.add(Pair(internalRequest, UniquenessCheckResultSuccessImpl(Clock.systemUTC().instant())))
            }

            backingStoreImpl.session { session ->
                session.executeTransaction { _, txnOps -> txnOps.commitTransactions(txns) }
            }

            backingStoreImpl.session { session ->
                val result = session.getTransactionDetails(txIds)
                assertEquals(txCnt, result.size)
                result.forEach { secureHashTxnDetails ->
                    assertTrue(secureHashTxnDetails.key in txIds.toSet())
                    assertTrue(secureHashTxnDetails.value.result.toCharacterRepresentation() == 'A')
                }
            }
        }

        @Test
        fun `Persisting rejected transaction details succeeds`() {
            val txns = LinkedList<Pair<UniquenessCheckRequestInternal, UniquenessCheckResult>>()
            val txIds = LinkedList<SecureHash>()

            val txId = SecureHashUtils.randomSecureHash()
            val externalRequest = generateExternalRequest(txId)
            txIds.add(txId)

            val internalRequest = UniquenessCheckRequestInternal.create(externalRequest)
            txns.add(
                Pair(
                    internalRequest, UniquenessCheckResultFailureImpl(
                        Clock.systemUTC().instant(), UniquenessCheckErrorGeneralImpl("some error")
                    )
                )
            )

            backingStoreImpl.session { session ->
                session.executeTransaction { _, txnOps -> txnOps.commitTransactions(txns) }
            }

            backingStoreImpl.session { session ->
                val result = session.getTransactionDetails(txIds)
                assertEquals(1, result.size)
                result.forEach { secureHashTxnDetails ->
                    assertTrue(secureHashTxnDetails.key in txIds.toSet())
                    assertTrue(secureHashTxnDetails.value.result.toCharacterRepresentation() == 'R')
                }
            }
        }

        @Test
        fun `Persisting unconsumed states succeeds`() {
            val hashCnt = 5
            val secureHashes = generateSecureHashes(hashCnt)
            val stateRefs = generateUniquenessCheckStateRef(secureHashes)

            backingStoreImpl.session { session ->
                session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
            }

            backingStoreImpl.session { session ->
                val result = session.getStateDetails(stateRefs).toList()
                assertEquals(hashCnt, result.size)
                result.forEach { stateRefAndStateDetailPair ->
                    assertTrue(stateRefAndStateDetailPair.first.txHash in secureHashes.toSet())
                }
            }
        }

        @Test
        fun `Consuming an unconsumed state succeeds`() {
            val hashCnt = 2
            val secureHashes = generateSecureHashes(hashCnt)
            val stateRefs = generateUniquenessCheckStateRef(secureHashes)

            // Generate unconsumed states in DB.
            backingStoreImpl.session { session ->
                session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
            }

            // Consume one of unconsumed states in DB.
            val consumingTxId: SecureHash = secureHashes[0]
            val consumingStateRef = UniquenessCheckStateRefImpl(consumingTxId, 0)
            val consumingStateRefs = LinkedList<UniquenessCheckStateRef>()
            consumingStateRefs.push(consumingStateRef)
            backingStoreImpl.session { session ->
                session.executeTransaction { _, txnOps ->
                    txnOps.consumeStates(consumingTxId = consumingTxId, stateRefs = consumingStateRefs)
                }
            }

            // Verify if the target state has been correctly updated.
            backingStoreImpl.session { session ->
                val stateDetails = session.getStateDetails(stateRefs)
                val filteredStates = stateDetails.filterValues { it.consumingTxId != null }
                assertEquals(1, filteredStates.count())
                assertEquals(consumingTxId, filteredStates[consumingStateRef]?.consumingTxId)
            }
        }

        @Test
        fun `Double spend is prevented`() {
            val hashCnt = 1
            val secureHashes = generateSecureHashes(hashCnt)
            val stateRefs = generateUniquenessCheckStateRef(secureHashes)

            // Generate an unconsumed state in DB.
            backingStoreImpl.session { session ->
                session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
            }

            val consumingTxId = secureHashes[0]
            val consumingStateRef = UniquenessCheckStateRefImpl(consumingTxId, 0)
            val consumingStateRefs = LinkedList<UniquenessCheckStateRef>()
            consumingStateRefs.push(consumingStateRef)

            assertDoesNotThrow {
                backingStoreImpl.session { session ->
                    session.executeTransaction { _, txnOps ->
                        txnOps.consumeStates(consumingTxId = consumingTxId, stateRefs = consumingStateRefs)
                    }
                }
            }

            // An attempt to spend an already spent state should fail.
            assertThrows<IllegalStateException> {
                backingStoreImpl.session { session ->
                    session.executeTransaction { _, txnOps ->
                        txnOps.consumeStates(consumingTxId = consumingTxId, stateRefs = consumingStateRefs)
                    }
                }
            }
        }

        @Test
        fun `Attempt to consume an unknown state fails with an exception`() {
            val hashCnt = 2
            val secureHashes = generateSecureHashes(hashCnt)
            val stateRefs = generateUniquenessCheckStateRef(secureHashes)

            // Generate unconsumed states in DB.
            backingStoreImpl.session { session ->
                session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
            }

            val consumingTxId: SecureHash = SecureHashUtils.randomSecureHash()
            val consumingStateRef = UniquenessCheckStateRefImpl(consumingTxId, 0)
            val consumingStateRefs = LinkedList<UniquenessCheckStateRef>()
            consumingStateRefs.push(consumingStateRef)

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
        @Test
        fun `Query timeout triggers retry`() {
            val emFactory = createEntityManagerFactory("uniqueness_2")
            val spyEmFactory = Mockito.spy(emFactory)
            val em = spyEmFactory.createEntityManager()
            val spyEm = Mockito.spy(em)
            Mockito.doReturn(spyEm).whenever(spyEmFactory).createEntityManager()

            val queryName = "UniquenessTransactionDetailEntity.select"
            val resultClass = UniquenessTransactionDetailEntity::class.java
            // Find a way to mock a TypedQuery while make the logic JPA implementation agnostic.
            // Actual execution of the query happens at invoking resultList of the query.
            Mockito.doThrow(QueryTimeoutException("Executing a query timed out"))
                .whenever(spyEm).createNamedQuery(queryName, resultClass)

            val storeImpl = createBackingStoreImpl(spyEmFactory)
            storeImpl.eventHandler(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mock())

            assertThrows<QueryTimeoutException> {
                storeImpl.session { session ->
                    val txIds = LinkedList<SecureHash>()
                    txIds.add(SecureHashUtils.randomSecureHash())
                    session.getTransactionDetails(txIds)
                }
            }
            Mockito.verify(spyEm, times(maxAttemptsCnt)).createNamedQuery(queryName, resultClass)
        }

        @Test
        fun `Persistence errors triggers retry`() {
            val emFactory = createEntityManagerFactory("uniqueness_2")
            val spyEmFactory = Mockito.spy(emFactory)
            val em = spyEmFactory.createEntityManager()
            val spyEm = Mockito.spy(em)
            Mockito.doThrow(PersistenceException("Persistence error")).whenever(spyEm).persist(any())
            Mockito.doReturn(spyEm).whenever(spyEmFactory).createEntityManager()

            val storeImpl = createBackingStoreImpl(spyEmFactory)
            storeImpl.eventHandler(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mock())

            val secureHashes = generateSecureHashes(1)
            val stateRefs = generateUniquenessCheckStateRef(secureHashes)
            assertThrows<PersistenceException> {
                storeImpl.session { session ->
                    session.executeTransaction { _, txnOps -> txnOps.createUnconsumedStates(stateRefs) }
                }
            }
            Mockito.verify(spyEm, times(maxAttemptsCnt)).persist(any())
        }
    }
}