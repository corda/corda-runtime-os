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
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateRefImpl
import net.corda.uniqueness.datamodel.internal.UniquenessCheckRequestInternal
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.toAvro
import org.junit.jupiter.api.*
import org.mockito.Mockito
import org.mockito.kotlin.*
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*
import javax.persistence.EntityExistsException
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.OptimisticLockException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JPABackingStoreImplIntegrationTests {
    private lateinit var backingStoreImpl: JPABackingStoreImpl
    private val entityManager: EntityManager
    private val entityManagerFactory: EntityManagerFactory
    private val dbConfig = DbUtils.getEntityManagerConfiguration("uniqueness_default")

    private lateinit var lifecycleCoordinator: LifecycleCoordinator
    private lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory

    class DummyException(message: String) : Exception(message)

    init {
        entityManagerFactory = EntityManagerFactoryFactoryImpl().create(
            "uniqueness_default",
            JPABackingStoreEntities.classes.toList(),
            dbConfig
        )
        entityManager = entityManagerFactory.createEntityManager()
    }

    @BeforeEach
    fun init() {
        lifecycleCoordinator = mock<LifecycleCoordinator>()
        lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>().apply {
            whenever(createCoordinator(any(), any())) doReturn lifecycleCoordinator
        }

        backingStoreImpl = JPABackingStoreImpl(lifecycleCoordinatorFactory,
            JpaEntitiesRegistryImpl(),
            mock<DbConnectionManager>().apply {
                whenever(getOrCreateEntityManagerFactory(any(), any(), any())) doReturn entityManagerFactory
                whenever(getClusterDataSource()) doReturn dbConfig.dataSource
            })

        backingStoreImpl.eventHandler(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mock())
    }

    private fun generateSecureHashes(cnt: Int): LinkedList<SecureHash> {
        val secureHashes = LinkedList<SecureHash>()
        repeat(cnt) {
            secureHashes.push(SecureHashUtils.randomSecureHash())
        }
        return secureHashes
    }

    private fun generateInternalStateRefs(secureHashes: LinkedList<SecureHash>): LinkedList<UniquenessCheckStateRef> {
        val uniquenessCheckInternalStateRefs = secureHashes.let {
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
    inner class TransactionTests {
        @Test
        fun `Executing transaction retries upon expected exceptions`() {
            val maxRetryCount = 10
            var execCounter = 0
            assertThrows<IllegalStateException> {
                backingStoreImpl.session { session ->
                    session.executeTransaction { _, _ ->
                        execCounter++
                        throw EntityExistsException()
                    }
                }
            }
            assertEquals(maxRetryCount, execCounter)
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
    inner class LifeCycleTests {
        @Test
        fun `Starting backing store invokes life cycle start`() {
            Mockito.verify(lifecycleCoordinator, never()).start()
            backingStoreImpl.start()
            Mockito.verify(lifecycleCoordinator).start()
        }

        @Test
        fun `Stopping backing store invokes life cycle stop`() {
            Mockito.verify(lifecycleCoordinator, never()).stop()
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
    inner class PersistingDataTests {
        @Test
        fun `Persisting transaction details succeeds`() {
            val txCnt = 3
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
                }
            }
        }

        @Test
        fun `Persisting unconsumed states succeeds`() {
            val hashCnt = 5
            val secureHashes = generateSecureHashes(hashCnt)
            val stateRefs = generateInternalStateRefs(secureHashes)

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
            val stateRefs = generateInternalStateRefs(secureHashes)

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
        fun `Attempt to consume an unknown state fails with an exception`() {
            val hashCnt = 2
            val secureHashes = generateSecureHashes(hashCnt)
            val stateRefs = generateInternalStateRefs(secureHashes)

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
}