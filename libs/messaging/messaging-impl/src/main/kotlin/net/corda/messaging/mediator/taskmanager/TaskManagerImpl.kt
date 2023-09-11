package net.corda.messaging.mediator.taskmanager

import net.corda.messaging.api.mediator.taskmanager.TaskManager
import net.corda.messaging.api.mediator.taskmanager.TaskType
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.util.concurrent.CompletableFuture

@Component(service = [TaskManager::class])
class TaskManagerImpl  @Activate constructor() : TaskManager {
    override fun <T> execute(type: TaskType, command: () -> T): CompletableFuture<T> {
        TODO("Not yet implemented")
    }
}