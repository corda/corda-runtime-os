package net.corda.messaging.mediator.taskmanager

import net.corda.messaging.api.mediator.taskmanager.TaskManager
import net.corda.messaging.api.mediator.taskmanager.TaskType
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.concurrent.thread

// TODO This is used temporarily until Task Manager implementation is finished
@Component(service = [TaskManager::class])
class TaskManagerImpl  @Activate constructor() : TaskManager {
    private var executorService = Executors.newSingleThreadExecutor()

    override fun <T> execute(type: TaskType, command: () -> T) =
        when (type) {
            TaskType.SHORT_RUNNING -> executeShortRunning(command)
            TaskType.LONG_RUNNING -> executeLongRunning(command)
        }

    private fun <T> executeShortRunning(command: () -> T): CompletableFuture<T> {
        val result = CompletableFuture<T>()
        executorService.execute {
            result.complete(command())
        }
        return result
    }

    private fun <T> executeLongRunning(command: () -> T): CompletableFuture<T> {
        val uniqueId = UUID.randomUUID()
        val result = CompletableFuture<T>()
        thread(
            start = true,
            isDaemon = true,
            contextClassLoader = null,
            name = "Task Manager - $uniqueId",
            priority = -1,
        ) {
            result.complete(command())
        }
        return result
    }
}