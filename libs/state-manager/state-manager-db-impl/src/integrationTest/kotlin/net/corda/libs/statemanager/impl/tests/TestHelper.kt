package net.corda.libs.statemanager.impl.tests

import java.util.UUID
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.metadata

/**
 * Divide a list of states between threads so that each thread's group of states contains some states from the next
 * thread's group. A thread's group will contain unique states, plus [sharedStatesPerThread] states from the next group.
 *
 * Example: a list of [a, b, c, d, e, f, g, h, i] split among 3 threads with 1 shared states per thread results in:
 * [[a, b, c, d], [d, e, f, g], [g, h, i, a]]
 *
 * @param states a list of states to be divided
 * @param numThreads the number of thread groups to divide the states among
 * @param sharedStatesPerThread the number of states to be shared between thread groups, only one thread can successfully
 *  update a state with a given version.
 */
fun divideStatesBetweenThreads(
    states: List<State>,
    numThreads: Int,
    sharedStatesPerThread: Int
): List<List<State>> {
    require(states.size > numThreads) { "Must be at least one state per thread" }
    require(states.size % numThreads == 0) { "States should be evenly split across threads" }
    require(((states.size / numThreads) - sharedStatesPerThread) > 0) { "Must be at least one unique state per thread group" }

    // split states between thread such that all states are distributed
    val chunkedThreadGroups = states.chunked(states.size / numThreads)

    return List(chunkedThreadGroups.size) { index ->
        val currentGroup = chunkedThreadGroups[index].toMutableList()

        val nextIndex = (index + 1) % chunkedThreadGroups.size
        val nextThreadStates = chunkedThreadGroups[nextIndex]

        currentGroup.addAll(nextThreadStates.take(sharedStatesPerThread))
        currentGroup
    }
}

/**
 * Update a list of State objects with thread information.
 */
fun updateStateObjects(states: List<State>, testUniqueId: UUID) = states.map {
    State(
        it.key,
        "updatedState_${Thread.currentThread().id}".toByteArray(),
        it.version,
        metadata(
            "testRun" to testUniqueId.toString(),
            "threadId" to Thread.currentThread().id,
            "threadName" to Thread.currentThread().name,
        )
    )
}