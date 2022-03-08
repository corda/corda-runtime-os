package net.corda.membership.impl.read.cache

import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_1
import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_2
import net.corda.membership.impl.read.TestProperties.Companion.aliceName
import net.corda.membership.impl.read.TestProperties.Companion.bobName
import net.corda.membership.impl.read.TestProperties.Companion.charlieName
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

class MemberListCachePerformanceTest {
    private lateinit var memberListCache: MemberListCache

    private val alice = aliceName
    private val bob = bobName
    private val charlie = charlieName

    private val aliceIdGroup1 = HoldingIdentity(alice.toString(), GROUP_ID_1)
    private val bobIdGroup1 = HoldingIdentity(bob.toString(), GROUP_ID_1)
    private val charlieIdGroup1 = HoldingIdentity(charlie.toString(), GROUP_ID_1)
    private val aliceIdGroup2 = HoldingIdentity(alice.toString(), GROUP_ID_2)
    private val bobIdGroup2 = HoldingIdentity(bob.toString(), GROUP_ID_2)
    private val charlieIdGroup2 = HoldingIdentity(charlie.toString(), GROUP_ID_2)

    private lateinit var memberInfoAlice: MemberInfo
    private lateinit var memberInfoBob: MemberInfo
    private lateinit var memberInfoCharlie: MemberInfo

    /**
     * Test parameters.
     */
    private companion object {
        const val NUM_THREADS = 6
        const val NUM_TEST_REPETITIONS = 1000

        val numOperationsToExecuteArray = arrayOf(1000, 5000, 10_000, 50_000)
        val writeToReadRatios = arrayOf(0.01, 0.1, 0.2, 0.3, 0.4, 0.5)

        const val VERBOSE_OPERATION_OUTPUT = false
        const val PRINT_PROGRESS = true
        const val PRINT_PROGRESS_VERBOSE = false
        const val PRINT_RUN_SUMMARIES = false

        // If true, the performance test will iterate over the memberlist and read a property from each member
        // to try find concurrency issues with the nested list.
        const val ITERATE_READ_MEMBERLIST = true
    }

    /**
     * Set the cache implementation
     */
    private fun setUpCacheImplementation() {
        memberListCache = MemberListCache.Impl()
    }

    private val threadFactory = ThreadFactory {
        Executors.defaultThreadFactory().newThread(it).apply {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { t, e ->
                println(t.name + ": threw exception -> " + e.message)
            }
        }
    }

    private val clock = Clock.systemUTC()
    private lateinit var countDownLatch: CountDownLatch

    @Test
    @Disabled("Not necessary for automated builds. Can be enabled temporarily to re-run the performance test.")
    fun performanceTest() {
        writeToReadRatios.forEach {
            require(it in 0.0..1.0)
        }
        numOperationsToExecuteArray.forEach {
            require(it > 0)
        }
        require(NUM_TEST_REPETITIONS > 0)
        require(NUM_THREADS > 0)

        val runResults = mutableListOf<RunResults>()

        for (writeToReadRatio in writeToReadRatios) {
            for (numOperationsToExecute in numOperationsToExecuteArray) {
                if (PRINT_PROGRESS_VERBOSE || PRINT_PROGRESS) {
                    println("Executing $numOperationsToExecute operations with ratio $writeToReadRatio")
                }
                val runOutput = runTestCaseWithRepetition(numOperationsToExecute, writeToReadRatio)
                runResults.addAll(runOutput)
                printRunSummary(runOutput, numOperationsToExecute, writeToReadRatio)
            }
            println("==== PARTIAL RESULTS ====")
            printResults(runResults)
            println()
        }

        println("==== FINAL RESULTS ====")
        printResults(runResults)
    }

    /**
     * Create a member info mock.
     */
    private fun getMemberInfo(memberName: MemberX500Name) = mock<MemberInfo>().apply {
        whenever(name).thenReturn(memberName)
    }

    /**
     * Set up required mocks
     */
    private fun setUpMockObjects() {
        memberInfoAlice = getMemberInfo(alice)
        memberInfoBob = getMemberInfo(bob)
        memberInfoCharlie = getMemberInfo(charlie)
    }

    /**
     * Reset the countdown latch
     */
    private fun setCountDownLatch(numOperationsToExecute: Int) {
        countDownLatch = CountDownLatch(numOperationsToExecute)
    }

    /**
     * Create a [CacheOperation] instance which reads member list data from the cache and decrements the countdown
     * latch.
     * If configured to do so, the member list will be iterated to try catch concurrency issues with the nested member
     * list in the cache.
     */
    private fun read(holdingId: HoldingIdentity) = CacheOperation {
        try {
            val list = memberListCache.get(holdingId)
            if (ITERATE_READ_MEMBERLIST) {
                list?.forEach {
                    require(it.name.toString().isNotEmpty())
                }
            }
            if (VERBOSE_OPERATION_OUTPUT) println("${Thread.currentThread().name} - ${clock.millis()}: Read")
            countDownLatch.countDown()
        } catch (e: ConcurrentModificationException) {
            println("ConcurrentModificationException thrown")
            throw e
        }
    }

    /**
     * Create a [CacheOperation] instance which writes data to the cache and decrements the countdown latch.
     */
    private fun write(holdingId: HoldingIdentity, data: MemberInfo) = CacheOperation {
        memberListCache.put(holdingId, data)
        if (VERBOSE_OPERATION_OUTPUT) println("${Thread.currentThread().name} - ${clock.millis()}: Write")
        countDownLatch.countDown()
    }

    /**
     * Array of possible reads operations
     */
    private fun possibleReadOperations() = CacheOperations(
        listOf(
            read(aliceIdGroup1),
            read(aliceIdGroup2),
            read(bobIdGroup1),
            read(bobIdGroup2),
            read(charlieIdGroup1),
            read(charlieIdGroup2),
        )
    )

    /**
     * Array of possible write operations
     */
    private fun possibleWriteOperations() = CacheOperations(
        listOf(
            write(aliceIdGroup1, memberInfoAlice),
            write(aliceIdGroup1, memberInfoBob),
            write(aliceIdGroup1, memberInfoCharlie),
            write(aliceIdGroup2, memberInfoAlice),
            write(aliceIdGroup2, memberInfoBob),
            write(aliceIdGroup2, memberInfoCharlie),
            write(bobIdGroup1, memberInfoAlice),
            write(bobIdGroup1, memberInfoBob),
            write(bobIdGroup1, memberInfoCharlie),
            write(bobIdGroup2, memberInfoAlice),
            write(bobIdGroup2, memberInfoBob),
            write(bobIdGroup2, memberInfoCharlie),
            write(charlieIdGroup1, memberInfoAlice),
            write(charlieIdGroup1, memberInfoBob),
            write(charlieIdGroup1, memberInfoCharlie),
            write(charlieIdGroup2, memberInfoAlice),
            write(charlieIdGroup2, memberInfoBob),
            write(charlieIdGroup2, memberInfoCharlie),
        )
    )

    /**
     * Select a random operation from the given list of read or write operations
     */
    private fun getOperation(ops: CacheOperations) = ops[(Math.random() * ops.size).toInt()]

    /**
     * Run the performance test for the given parameters and repeat the test based on the static number of repetitions
     * set in the companion object.
     */
    private fun runTestCaseWithRepetition(
        numOperationsToExecute: Int,
        writeToReadRatio: Double
    ): List<RunResults> {
        val results = mutableListOf<RunResults>()
        // Iterate for number of repeated tests to get an average result
        for (i in 0 until NUM_TEST_REPETITIONS) {
            val quarter = NUM_TEST_REPETITIONS / 4
            if (i == quarter) println("25% done")
            if (i == quarter * 2) println("50% done")
            if (i == quarter * 3) println("75% done")
            setUpCacheImplementation()
            setUpMockObjects()
            setCountDownLatch(numOperationsToExecute)

            val cacheOperations = getCacheOperations(numOperationsToExecute, writeToReadRatio)
            val testDuration = runTestCase(cacheOperations)

            // Record run results
            results.add(
                RunResults(
                    writeToReadRatio,
                    numOperationsToExecute,
                    testDuration
                )
            )
            if (PRINT_PROGRESS_VERBOSE) println("End of run")
        }
        return results
    }

    /**
     * Run single performance test case for given cache operations.
     */
    private fun runTestCase(cacheOperations: CacheOperations): Long {
        val executorService = Executors.newFixedThreadPool(NUM_THREADS, threadFactory)

        if (PRINT_PROGRESS_VERBOSE) println("Starting test")
        // START PERFORMANCE TEST
        val start = clock.millis()
        executorService.invokeAll(cacheOperations)
        countDownLatch.await(10L, TimeUnit.SECONDS)
        val end = clock.millis()
        //END PERFORMANCE TEST
        if (PRINT_PROGRESS_VERBOSE) println("Ending test. Shutting down threads.")
        // Shutdown executor service
        executorService.shutdown()
        executorService.awaitTermination(10L, TimeUnit.SECONDS)
        return end - start
    }

    /**
     * Create the list of cache operations. This will return a list of operations which size is equal to
     * [numOperationsToExecute] and the ratio of write to read operations will equal [writeToReadRatio].
     * The resulting list will be shuffled to avoid running an identical test case multiple times.
     */
    private fun getCacheOperations(
        numOperationsToExecute: Int,
        writeToReadRatio: Double
    ): CacheOperations {
        // Calculate number N so that every Nth operation is a write (before shuffling the operations)
        @Suppress("DIVISION_BY_ZERO")
        val readOccurrence = when {
            writeToReadRatio > 0 -> (1 / writeToReadRatio).toInt()
            else -> 0
        }

        var writes = 0
        var reads = 0

        val output = CacheOperations((0 until numOperationsToExecute).map {
            if (readOccurrence > 0 && it % readOccurrence < writeToReadRatio) {
                writes++
                getOperation(possibleWriteOperations())
            } else {
                reads++
                getOperation(possibleReadOperations())
            }
        }.shuffled())
        require(output.size == numOperationsToExecute)

        if (PRINT_PROGRESS_VERBOSE) {
            println("Prepared $writes write operations and $reads read operations for testing.")
        }
        return output
    }

    /**
     * Print the run results in a table format. X-axis is the load, and Y-axis is the write to read ratio.
     */
    private fun printResults(runResults: List<RunResults>) {
        val resultsGroupedByRatios = runResults.groupBy { it.writeToReadRatio }.toSortedMap()

        var rowNum = 0
        resultsGroupedByRatios.forEach { (t, u) ->
            val groupedByOperationsExecuted = u.groupBy { it.operationsExecuted }.toSortedMap()

            if (rowNum == 0) {
                print("\t")
                groupedByOperationsExecuted.forEach { print("${it.key}\t") }
                print("\n")
            }

            print("$t\t")
            groupedByOperationsExecuted.forEach { print("${it.value.sumOf { it.timeTaken } / it.value.size}ms\t") }
            rowNum++
            print("\n")
        }
    }

    /**
     * Print the summary of a given run.
     */
    private fun printRunSummary(
        runResults: List<RunResults>,
        numOperationsToExecute: Int,
        writeToReadRatio: Double
    ) {
        if (PRINT_RUN_SUMMARIES) {
            val averageProcessingTime = runResults.filter {
                it.writeToReadRatio == writeToReadRatio && it.operationsExecuted == numOperationsToExecute
            }.let { results -> results.sumOf { it.timeTaken } / results.size }

            val averageOperationsPerSecond =
                ((numOperationsToExecute.toDouble() / averageProcessingTime) * 1000)

            /**
             * Print results.
             */
            println(
                """
                ************************
                RUN STATISTICS:
                
                Average processing time: ${averageProcessingTime}ms
                Average operations per second: $averageOperationsPerSecond
                Number of threads: $NUM_THREADS
                Number of operations performed: $numOperationsToExecute
                Ratio of writes to reads: $writeToReadRatio
                Number of test runs: $NUM_TEST_REPETITIONS
                ************************
            """.trimIndent()
            )
        }
    }

    private class CacheOperations(ops: List<CacheOperation>) : List<CacheOperation> by ops
    private class CacheOperation(op: Callable<Unit>) : Callable<Unit> by op

    private data class RunResults(
        val writeToReadRatio: Double,
        val operationsExecuted: Int,
        val timeTaken: Long
    )
}
