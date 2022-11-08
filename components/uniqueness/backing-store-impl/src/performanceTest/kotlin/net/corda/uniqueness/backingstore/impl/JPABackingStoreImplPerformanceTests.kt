package net.corda.uniqueness.backingstore.impl

import net.corda.crypto.testkit.SecureHashUtils
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.db.testkit.DatabaseInstaller
import net.corda.db.testkit.DbUtils
import net.corda.db.testkit.TestDbInfo
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.impl.JpaEntitiesRegistryImpl
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.AutoTickTestClock
import net.corda.uniqueness.backingstore.BackingStore
import net.corda.uniqueness.backingstore.jpa.datamodel.JPABackingStoreEntities
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorMalformedRequestImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateRefImpl
import net.corda.uniqueness.datamodel.internal.UniquenessCheckRequestInternal
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

@Execution(ExecutionMode.SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName::class)
class JPABackingStoreImplPerformanceTests {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private val numIterations = System.getProperty("perfTestNumIterations").toInt()
    private val numOpsPerIter = System.getProperty("perfTestNumOpsPerIteration").toInt()

    private val clusterDbConfig =
        DbUtils.getEntityManagerConfiguration(inMemoryDbName = "clusterdb", showSql = false)

    private var currentTestExecTimeMs = 0L
    private val resultsMap = TreeMap<String, Int>()

    private lateinit var holdingIdentity: HoldingIdentity
    private lateinit var backingStore: BackingStore
    private lateinit var testClock: AutoTickTestClock

    @BeforeAll
    fun printArguments() {
        log.info("Executing performance tests with $numIterations iterations, $numOpsPerIter " +
                "operations per iteration.")
    }

    @BeforeEach
    fun init() {
        // An entirely new database is created for each test case, to ensure performance is not
        // impacted between different test cases
        holdingIdentity = createTestHoldingIdentity(
            "C=GB, L=London, O=Alice", UUID.randomUUID().toString())
         val holdingIdentityDbName =
            VirtualNodeDbType.UNIQUENESS.getSchemaName(holdingIdentity.shortHash)

        val databaseInstaller = DatabaseInstaller(
            EntityManagerFactoryFactoryImpl(),
            LiquibaseSchemaMigratorImpl(),
            JpaEntitiesRegistryImpl()
        )

        log.info("Uniqueness DB name is $holdingIdentityDbName")

        // Each DB uses both a different db name and schema name, as HSQLDB does not appear to
        // respect schema name
        val holdingIdentityDb = databaseInstaller.setupDatabase(
            TestDbInfo(
                name = holdingIdentityDbName,
                schemaName = holdingIdentityDbName,
                showSql = false),
            "vnode-uniqueness",
            JPABackingStoreEntities.classes
        )

        backingStore = JPABackingStoreImpl(
            mock(),
            JpaEntitiesRegistryImpl(),
            mock<DbConnectionManager>().apply {
                whenever(getOrCreateEntityManagerFactory(
                    eq(holdingIdentityDbName), any(), any()
                )) doReturn holdingIdentityDb
                whenever(getClusterDataSource()) doReturn clusterDbConfig.dataSource
            }
        ).apply {
            eventHandler(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mock())
        }

        testClock = AutoTickTestClock(Instant.EPOCH, Duration.ofSeconds(1))
    }

    @AfterAll
    fun resultsSummary() {
        log.info("Tests complete. Summary: " +
                "${resultsMap.map {"\"${it.key}\" : ${it.value} ops/sec"}}")
    }

    @Test
    fun `state read performance`() {
        executeTest {
            createUnconsumedStates().let { unconsumedStates ->
                measure {
                    backingStore.session(holdingIdentity) { session ->
                        session.getStateDetails(unconsumedStates)
                    }
                }
            }
        }
    }

    @Test
    fun `state write performance - unconsumed`() {
        executeTest { measure { createUnconsumedStates() } }
    }

    @Test
    fun `state write performance - consumed`() {
        executeTest {
            createUnconsumedStates().let { unconsumedStates ->
                measure {
                    backingStore.transactionSession(holdingIdentity) { _, txOps ->
                        txOps.consumeStates(SecureHashUtils.randomSecureHash(), unconsumedStates)
                    }
                }
            }
        }
    }

    @Test
    fun `transaction read performance - successful`() {
        executeTest {
            createTransactionRecords(true).let { txIds ->
                measure {
                    backingStore.session(holdingIdentity) { session ->
                        session.getTransactionDetails(txIds)
                    }
                }
            }
        }
    }

    @Test
    fun `transaction read performance - rejected`() {
        executeTest {
            createTransactionRecords(false).let { txIds ->
                measure {
                    backingStore.session(holdingIdentity) { session ->
                        session.getTransactionDetails(txIds)
                    }
                }
            }
        }
    }

    @Test
    fun `transaction write performance - successful`() {
        executeTest { measure { createTransactionRecords(true) } }
    }

    @Test
    fun `transaction write performance - rejected`() {
        executeTest { measure { createTransactionRecords(false) } }
    }

    private fun createUnconsumedStates() : List<UniquenessCheckStateRef> {
        val states = List(numOpsPerIter) {
            UniquenessCheckStateRefImpl(SecureHashUtils.randomSecureHash(), 0)
        }

        backingStore.transactionSession(holdingIdentity) { _, txOps ->
            txOps.createUnconsumedStates(states)
        }

        return states
    }

    private fun createTransactionRecords(successful: Boolean) : List<SecureHash> {
        val txIds = List(numOpsPerIter) { SecureHashUtils.randomSecureHash() }

        backingStore.transactionSession(holdingIdentity) { _, txOps ->
            txOps.commitTransactions(
                txIds.map { txId ->
                    Pair(
                        UniquenessCheckRequestInternal(
                            txId,
                            txId.toString(),
                            emptyList(),
                            emptyList(),
                            0,
                        null,
                            LocalDate.of(2200, 1, 1)
                                .atStartOfDay()
                                .toInstant(ZoneOffset.UTC)),
                        if (successful) {
                            UniquenessCheckResultSuccessImpl(testClock.instant())
                        }
                        else {
                            UniquenessCheckResultFailureImpl(
                                testClock.instant(),
                                UniquenessCheckErrorMalformedRequestImpl("Test error")
                            )
                        }
                    )
                }
            )
        }

        return txIds
    }

    private fun executeTest(block: () -> Unit) {
        currentTestExecTimeMs = 0L

        repeat(numIterations) { block.invoke() }

        val averageRate = getAverageRate(currentTestExecTimeMs)

        log.info("Completed $numIterations iterations of $numOpsPerIter operations in " +
                "${currentTestExecTimeMs}ms. Average $averageRate ops/sec.")

        resultsMap[Thread.currentThread().stackTrace[2].methodName] = averageRate
    }

    private fun measure(block: () -> Unit) {
        currentTestExecTimeMs += measureTimeMillis(block)
    }

    private fun getAverageRate(execTimeMs: Long): Int {
        return ((numOpsPerIter * numIterations) / (execTimeMs.toDouble() / 1000)).roundToInt()
    }
}
