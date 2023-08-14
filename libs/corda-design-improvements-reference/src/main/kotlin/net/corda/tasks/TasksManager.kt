package net.corda.tasks

import net.corda.tasks.models.CommandType
import java.util.concurrent.CompletableFuture

interface TasksManager {
    fun getNumberOfThreads(): Int
    fun waitForAllJobs(): CompletableFuture<Void>
    fun cancelAllJobs(): CompletableFuture<Void>
    fun shutdown(): CompletableFuture<Void>
    fun <T> execute(command: () -> T, type: CommandType): CompletableFuture<T>
}