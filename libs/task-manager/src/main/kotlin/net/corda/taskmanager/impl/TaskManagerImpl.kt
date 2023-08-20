package net.corda.taskmanager.impl

import net.corda.taskmanager.TaskManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

internal class TaskManagerImpl(
    private val executorService: ExecutorService,
) : TaskManager {

    override fun shutdown(): CompletableFuture<Void> {
        executorService.shutdown()
        // this completablefuture must not run on the executor service otherwise it'll never shut down.
        // runAsync runs this task in the fork join common pool.
        val shutdownFuture = CompletableFuture.runAsync {
            executorService.awaitTermination(100, TimeUnit.SECONDS)
        }
        return shutdownFuture
    }

    override fun <T> execute(type: TaskManager.CommandType, command: () -> T): CompletableFuture<T> {
        return CompletableFuture.supplyAsync({ command.invoke() }, executorService)
    }

    override fun execute(command: Runnable) {
        executorService.execute(command)
    }
}