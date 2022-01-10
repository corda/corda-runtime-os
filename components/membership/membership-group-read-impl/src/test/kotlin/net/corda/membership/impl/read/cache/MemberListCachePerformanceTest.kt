package net.corda.membership.impl.read.cache

import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_1
import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_2
import net.corda.membership.impl.read.TestProperties.Companion.aliceName
import net.corda.membership.impl.read.TestProperties.Companion.bobName
import net.corda.membership.impl.read.TestProperties.Companion.charlieName
import net.corda.v5.membership.identity.MemberInfo
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
    companion object {
        const val numThreads = 6
        const val testRuns = 1000

        val numOperationsToExecuteArray = arrayOf(1000, 5000, 10_000, 50_000)
        val writeToReadRatios = arrayOf(0.1, 0.2, 0.3, 0.4, 0.5)

        const val verboseOperationOutputs = false
        const val printProgress = true
        const val verbosePrintProgress = false
        const val printSummary = false
    }

    private val threadFactory = ThreadFactory {
        Executors.defaultThreadFactory().newThread(it).apply {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { t, e ->
                println(t.name + ": threw exception -> " + e.message)
            }
        }
    }

    val clock = Clock.systemUTC()

    lateinit var countDownLatch: CountDownLatch

    data class RunResults(
        val writeToReadRatio: Double,
        val operationsExecuted: Int,
        val writes: Int,
        val reads: Int,
        val timeTaken: Long
    )

    fun setUpCache() {
        memberListCache = MemberListCache.Impl()
    }

    fun setUpMockObjects() {
        memberInfoAlice = mock<MemberInfo>().apply {
            whenever(name).thenReturn(alice)
        }
        memberInfoBob = mock<MemberInfo>().apply {
            whenever(name).thenReturn(bob)
        }
        memberInfoCharlie = mock<MemberInfo>().apply {
            whenever(name).thenReturn(charlie)
        }
    }

    fun read(holdingId: HoldingIdentity) = Callable {
        memberListCache.get(holdingId)
        if (verboseOperationOutputs) println("${Thread.currentThread().name} - ${clock.millis()}: Read")
        countDownLatch.countDown()
    }

    fun write(holdingId: HoldingIdentity, data: MemberInfo) = Callable {
        memberListCache.put(holdingId, data)
        if (verboseOperationOutputs) println("${Thread.currentThread().name} - ${clock.millis()}: Write")
        countDownLatch.countDown()
    }

    /**
     * Array of possible reads operations
     */
    fun possibleReadOperations() = arrayOf(
        read(aliceIdGroup1),
        read(aliceIdGroup2),
        read(bobIdGroup1),
        read(bobIdGroup2),
        read(charlieIdGroup1),
        read(charlieIdGroup2),
    )

    fun possibleWriteOperations() = arrayOf(
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

    fun getOperation(ops: Array<Callable<Unit>>) = ops[(Math.random() * ops.size).toInt()]

    @Test
    @Disabled("Not necessary for automated builds. Can be enabled temporarily to re-run the performance test.")
    fun performanceTest() {
        writeToReadRatios.forEach {
            require(it in 0.0..1.0)
        }
        numOperationsToExecuteArray.forEach {
            require(it > 0)
        }
        require(testRuns > 0)
        require(numThreads > 0)

        val runResults = mutableListOf<RunResults>()

        for (writeToReadRatio in writeToReadRatios) {
            for (numOperationsToExecute in numOperationsToExecuteArray) {

                if (printProgress) println("Executing $numOperationsToExecute operations with ratio $writeToReadRatio")

                // Calculate number N so that every Nth operation is a write (before shuffling the operations)
                @Suppress("DIVISION_BY_ZERO")
                val readOccurrence = when {
                    writeToReadRatio > 0 -> (1 / writeToReadRatio).toInt()
                    else -> 0
                }

                // Iterate for number of repeated tests to get an average result
                for (i in 1 until testRuns + 1) {
                    setUpCache()
                    setUpMockObjects()
                    countDownLatch = CountDownLatch(numOperationsToExecute)

                    var writeCount = 0
                    var readCount = 0

                    val runnableTasks = (0 until numOperationsToExecute).map {
                        if (readOccurrence > 0 && it % readOccurrence < writeToReadRatio) {
                            writeCount++
                            getOperation(possibleWriteOperations())
                        } else {
                            readCount++
                            getOperation(possibleReadOperations())
                        }
                    }.shuffled()

                    require(runnableTasks.size == numOperationsToExecute)

                    val executorService = Executors.newFixedThreadPool(numThreads, threadFactory)

                    if (verbosePrintProgress) println("Starting test")
                    // START PERFORMANCE TEST
                    val start = clock.millis()
                    executorService.invokeAll(runnableTasks)
                    countDownLatch.await()
                    val end = clock.millis()
                    //END PERFORMANCE TEST
                    if (verbosePrintProgress) println("Ending test. Shutting down threads.")
                    // Shutdown executor service
                    executorService.shutdown()
                    executorService.awaitTermination(10L, TimeUnit.SECONDS)

                    if (verbosePrintProgress) println("Recording results")
                    // Record run results
                    runResults.add(
                        RunResults(
                            writeToReadRatio,
                            numOperationsToExecute,
                            writeCount,
                            readCount,
                            end - start
                        )
                    )
                    if (verbosePrintProgress) println("End of run")
                }

                runResults.filter { it.writeToReadRatio == writeToReadRatio && it.operationsExecuted == numOperationsToExecute }
                val averageProcessingTime = runResults.filter {
                    it.writeToReadRatio == writeToReadRatio && it.operationsExecuted == numOperationsToExecute
                }.let { results -> results.sumOf { it.timeTaken } / results.size }

                val averageOperationsPerSecond =
                    ((numOperationsToExecute.toDouble() / averageProcessingTime) * 1000)

                if (printSummary) {
                    /**
                     * Print results.
                     */
                    println(
                        """
                ************************
                RUN STATISTICS:
                
                Average processing time: ${averageProcessingTime}ms
                Average operations per second: $averageOperationsPerSecond
                Number of threads: $numThreads
                Number of operations performed: $numOperationsToExecute
                Ratio of writes to reads: $writeToReadRatio
                Number of test runs: $testRuns
                ************************
            """.trimIndent()
                    )
                }
            }
            println("==== PARTIAL RESULTS ====")
            printResults(runResults)
            println()
        }

        println("==== FINAL RESULTS ====")
        printResults(runResults)
    }
    fun printResults(runResults: List<RunResults>) {
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
            groupedByOperationsExecuted.forEach { print("${it.value.sumOf { it.timeTaken} / it.value.size}ms\t") }
            rowNum++
            print("\n")
        }
    }
}
