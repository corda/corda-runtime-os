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
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TreeMap
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

/**
 * Tests the performance of the JPA backing store implementation against a real database
 */
@Execution(ExecutionMode.SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName::class)
class JPABackingStoreImplBenchmark {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    // Controls how many times we invoke the backing store API. Increasing this should not impact
    // the performance figures returned by the tests, but more iterations results in a longer
    // running test which may reduce variability in results.
    private val numIterations = System.getProperty("bsBenchNumIterations").toInt()
    // Controls how many operations (states or transactions, depending on the test) are passed into
    // a single call to the backing store. This number simulates processing a batch of requests as
    // part of the same database operation.
    private val numOpsPerIter = System.getProperty("bsBenchNumOpsPerIteration").toInt()

    private val clusterDbConfig =
        DbUtils.getEntityManagerConfiguration(inMemoryDbName = "clusterdb", showSql = false)

    private var currentTestExecTimeMs = 0L
    private val resultsMap = TreeMap<String, Int>()

    private lateinit var holdingIdentity: HoldingIdentity
    private lateinit var backingStore: BackingStore
    private lateinit var testClock: AutoTickTestClock

    @BeforeAll
    fun printArguments() {
        log.info("Executing benchmarks with $numIterations iterations, $numOpsPerIter operations " +
                "per iteration.")
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
                showSql = false,
                rewriteBatchedInserts = true),
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
        log.info("Benchmarks complete. Summary: " +
                "${resultsMap.map {"\"${it.key}\" : ${it.value} ops/sec"}}")
        writeToCsv()
    }

    @Test
    fun `state read performance`() {
        executeTest {
            createUnconsumedStates().let { unconsumedStates ->
                measure {
                    // We use transactionSession instead of session despite not performing write
                    // operations because this more closely reflects the backing store usage by the
                    // uniqueness checker, which performs all reads and writes in one session. Also,
                    // Hibernate appears to try to automatically create and rollback transactions
                    // if not executing statements in an existing transaction scope, which has a
                    // significant performance penalty.
                    backingStore.transactionSession(holdingIdentity) { session, _ ->
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
                    // We use transactionSession instead of session for the same reasons as
                    // mentioned earlier
                    backingStore.transactionSession(holdingIdentity) { session, _ ->
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
                    // We use transactionSession instead of session for the same reasons as
                    // mentioned earlier
                    backingStore.transactionSession(holdingIdentity) { session, _ ->
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

    /**
     * Creates unconsumed states, based on the configured number of operations, and returns the
     * state objects.
     */
    private fun createUnconsumedStates() : List<UniquenessCheckStateRef> {
        val states = List(numOpsPerIter) {
            UniquenessCheckStateRefImpl(SecureHashUtils.randomSecureHash(), 0)
        }

        backingStore.transactionSession(holdingIdentity) { _, txOps ->
            txOps.createUnconsumedStates(states)
        }

        return states
    }

    /**
     * Creates transaction records, based on the configured number of operations, and returns the
     * transaction ids.
     *
     * @param successful Whether the transaction is a successful or rejected transactions. For
     *                   rejected transactions, an additional error record will be constructed.
     */
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

    /**
     * Helper to execute a test case based on the configured number of iterations, and to record
     * performance figures. All code of a test case (including any setup) should be included within
     * the block of this function.
     */
    private fun executeTest(block: () -> Unit) {
        currentTestExecTimeMs = 0L

        repeat(numIterations) { block.invoke() }

        val averageRate = getAverageRate(currentTestExecTimeMs)

        log.info("Completed $numIterations iterations of $numOpsPerIter operations in " +
                "${currentTestExecTimeMs}ms. Average $averageRate ops/sec.")

        // We want to store the result against the test scenario name, but do not have the context
        // in this function, so we retrieve the test name by looking at the caller on the stack
        StackWalker.getInstance()
           .walk { frames -> frames.skip(1).findFirst().map { it.methodName } }
           .ifPresent { resultsMap[it] = averageRate  }
    }

    /**
     * Measures and records a specific iteration of a test case. This must be executed within an
     * [executeTest] block, and should only wrap code that you wish to measure the performance of,
     * i.e. excluding any test setup steps.
     */
    private fun measure(block: () -> Unit) {
        currentTestExecTimeMs += measureTimeMillis(block)
    }

    private fun getAverageRate(execTimeMs: Long): Int {
        return ((numOpsPerIter * numIterations) / (execTimeMs.toDouble() / 1000)).roundToInt()
    }

    private fun writeToCsv() {
        var file = File("${System.getProperty("java.io.tmpdir")}/test-results/" +
                "backingStoreBenchmark/results.csv")
        file.createNewFile()

        val headerRow = "Time,DB Type,Num Iterations,Ops per iteration," +
                resultsMap.keys.joinToString(",")

        file.bufferedReader().use { reader ->
            val existingHeaderRow = reader.readLine()

            if (headerRow != existingHeaderRow) {
                if (existingHeaderRow != null) {
                    // Existing file with a mismatching header row. Raise a warning and use a new
                    // file.
                    file = File("${System.getProperty("java.io.tmpdir")}/test-results" +
                            "/backingStoreBenchmark/results-${Instant.now().toEpochMilli()}.csv")
                    log.warn("Existing test results file found, but with different test cases. " +
                            "Writing to ${file.name}")
                    file.createNewFile()
                }

                // Write new header row
                file.writeText(headerRow + "\n")
            }
        }

        // Now have a valid file with header, write new row with the results of this run
        file.appendText("${Instant.now()}," +
                "${if (DbUtils.isInMemory) "HSQLDB" else "Postgres"}," +
                "$numIterations,$numOpsPerIter,${resultsMap.values.joinToString(",")}\n")

        log.info("Results written to ${file.canonicalPath}")
    }
}
