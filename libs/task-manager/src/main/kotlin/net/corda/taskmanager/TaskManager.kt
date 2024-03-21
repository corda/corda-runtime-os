package net.corda.taskmanager

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Future

interface TaskManager : Executor {
    fun <T> executeShortRunningTask(command: () -> T): Future<T>
    fun <T> executeLongRunningTask(command: () -> T): CompletableFuture<T>
    fun shutdown(): CompletableFuture<Void>
}