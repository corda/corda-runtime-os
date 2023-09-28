package net.corda.taskmanager

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

interface TaskManager : Executor {
    enum class TaskType {
        SHORT_RUNNING, LONG_RUNNING, SCHEDULED
    }
    fun shutdown(): CompletableFuture<Void>
    fun <T> execute(type: TaskType, command: () -> T): CompletableFuture<T>
}