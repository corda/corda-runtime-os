package net.corda.flow.taskmanager

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

interface TaskManager : Executor {
    enum class CommandType {
        FLOW, LONG_RUNNING, SCHEDULED
    }
    fun shutdown(): CompletableFuture<Void>
    fun <T> execute(type: CommandType, command: () -> T): CompletableFuture<T>
}