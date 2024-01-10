package net.corda.libs.statemanager.impl.tests

import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.metadata
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

object MultiThreadedTestHelper {
    /**
     * A group of states along with the states in the group that are overlapping with the next group.
     */
    data class StateGrouping(
        /**
         * A list of States assigned to this thread, not including the overlapping states to test optimistic locking.
         */
        val assignedStates: List<State>,
        /**
         * The list of States in this group that overlap with another group. These are states that will have contention
         * with other group and can be used for assertions on failed keys.
         */
        val overlappingStates: List<State>
    ) {
        /**
         * States to test optimistic locking, with some overlapping into other group's States.
         */
        fun getStatesForTest() = assignedStates + overlappingStates
    }

    /**
     * A summary of a thread's assigned state group and keys that failed to update/delete in a multithreaded optimistic
     * locking test.
     */
    data class ThreadTestSummary(
        /**
         * Group of states a thread was assigned.
         */
        val assignedStateGrouping: StateGrouping,
        /**
         * Keys that failed due to optimistic locking.
         */
        val failedKeysSummary: FailedKeysSummary
    )

    /**
     * Summary of keys that failed to update and delete in a request to the State Manager.
     */
    data class FailedKeysSummary(
        /**
         * Keys that failed to be updated by this thread due to optimistic locking.
         */
        val failedUpdates: List<String> = emptyList(),
        /**
         * Keys of States that failed to be deleted by this thread due to optimistic locking.
         */
        val failedDeletes: List<String> = emptyList()
    )

    /**
     * Update a list of State objects with thread information.
     */
    fun updateStateObjects(states: List<State>, testUniqueId: UUID, threadIndex: Int) = states.map {
        State(
            it.key,
            "updatedState_${Thread.currentThread().id}".toByteArray(),
            it.version,
            metadata(
                "threadIndex" to threadIndex,
                "testRun" to testUniqueId.toString(),
                "threadId" to Thread.currentThread().id,
                "threadName" to Thread.currentThread().name,
            )
        )
    }

    /**
     * Divide the given states between the number of threads, such that each group of states has overlapping states
     * with the next group in round-robin fashion. The last group in the list will have overlapping states with the
     * first group.
     *
     * Runs the [block] in an executor with the set [numThreads].
     *
     * @param states a list of States to be included in the test
     * @param numThreads the number of threads to test
     * @param sharedStatesPerThread the number of overlapping states per thread to exercise optimistic locking
     * @param block the test code which utilizes StateManager
     * @return a test summary for each thread, including each thread's assigned States plus its State keys that failed
     *  due to optimistic locking.
     */
    fun runMultiThreadedOptimisticLockingTest(
        states: List<State>,
        numThreads: Int,
        sharedStatesPerThread: Int,
        block: (threadIndex: Int, stateGroup: StateGrouping) -> FailedKeysSummary
    ): List<ThreadTestSummary> {
        val groupedStates = divideStatesBetweenThreads(states, numThreads, sharedStatesPerThread)

        val executor = Executors.newFixedThreadPool(numThreads)
        val futures = executor.invokeAll(
            (0 until numThreads).mapIndexed { threadId, threadGroup ->
                Callable {
                    block(threadId, groupedStates[threadGroup])
                }
            }
        )

        val failedKeysPerThread = futures.map { it.get() }
        return groupedStates.zip(failedKeysPerThread).map {
            ThreadTestSummary(it.first, it.second)
        }
    }

    /**
     * Divide a list of states between threads so that each thread's group of states contains some states from the next
     * thread's group, in round-robin fashion. A thread's group will contain unique states, plus [sharedStatesPerThread]
     * states from the next group.
     *
     * Example: a list of [a, b, c, d, e, f, g, h, i] split among 3 threads with 1 shared states per thread results in:
     * [[a, b, c, d], [d, e, f, g], [g, h, i, a]]
     *
     * @param states a list of states to be divided
     * @param numThreads the number of thread groups to divide the states among
     * @param sharedStatesPerThread the number of states to be shared between thread groups, only one thread can
     *  successfully update a state with a given version.
     */
    private fun divideStatesBetweenThreads(
        states: List<State>,
        numThreads: Int,
        sharedStatesPerThread: Int
    ): List<StateGrouping> {
        require(states.size > numThreads) { "Must be at least one state per thread" }
        require(states.size % numThreads == 0) { "States should be evenly split across threads" }
        require(((states.size / numThreads) - sharedStatesPerThread) > 0) { "Must be at least one unique state per thread group" }

        // split states between thread such that all states are distributed
        val chunkedThreadGroups = states.chunked(states.size / numThreads)

        return List(chunkedThreadGroups.size) { index ->
            val currentGroup = chunkedThreadGroups[index].toMutableList()

            val nextIndex = (index + 1) % chunkedThreadGroups.size
            val nextThreadStates = chunkedThreadGroups[nextIndex]

            val overlappingStates = nextThreadStates.take(sharedStatesPerThread)
            StateGrouping(currentGroup, overlappingStates)
        }
    }
}
