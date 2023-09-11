package net.corda.messaging.api.mediator.taskmanager

import java.util.concurrent.CompletableFuture

enum class TaskType {
    SHORT_RUNNING, LONG_RUNNING
}
interface TaskManager {
    fun <T> execute(type: TaskType, command: () -> T): CompletableFuture<T>
}