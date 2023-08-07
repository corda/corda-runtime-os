package net.corda.tasks

interface TaskAllocationStrategy {
    fun <T: Any> allocate(tasks: Set<T>): List<Set<T>>
}