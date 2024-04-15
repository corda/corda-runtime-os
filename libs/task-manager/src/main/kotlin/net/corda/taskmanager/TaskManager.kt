package net.corda.taskmanager

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

interface TaskManager : Executor {
    fun <T : Any> executeShortRunningTask(
        key: Any,
        priority: Long,
        persistedFuture: CompletableFuture<Unit>,
        forceFirst: Boolean,
        command: () -> T
    ): CompletableFuture<T>

    fun <T> executeLongRunningTask(command: () -> T): CompletableFuture<T>
    fun shutdown(): CompletableFuture<Void>
}