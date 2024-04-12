package net.corda.taskmanager

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Future

interface TaskManager : Executor {
    fun <T : Any> executeShortRunningTask(
        key: Any,
        priority: Long,
        commandTimeout: Duration, // This is not how long to wait in the queue, but how long to wait once started
        command: () -> T
    ): Future<T>

    fun <T> executeLongRunningTask(command: () -> T): CompletableFuture<T>
    fun shutdown(): CompletableFuture<Void>
}