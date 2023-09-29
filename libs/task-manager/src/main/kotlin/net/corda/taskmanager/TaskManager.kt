package net.corda.taskmanager

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

interface TaskManager : Executor {
    fun shutdown(): CompletableFuture<Void>
    fun <T> executeShortRunningTask(command: () -> T): CompletableFuture<T>
    fun <T> executeLongRunningTask(command: () -> T): CompletableFuture<T>
    fun <T> executeScheduledTask(command: () -> T, delay: Long, unit: TimeUnit): CompletableFuture<T>
}