package net.corda.applications.flowworker.setup.tasks

import net.corda.applications.flowworker.setup.Task
import net.corda.applications.flowworker.setup.TaskContext
import org.slf4j.Logger

class Tasks(
    context: TaskContext,
    private val log: Logger) {

    private val taskList: Map<String, Task>

    init {
        taskList = mutableMapOf(
            add(CreateTopics(context)),
            add(DeleteAllTopics(context)),
            add(PublishConfig(context)),
            add(StartFlow(context)),
            add(SetupVirtualNode(context)),
            add(StartTokenFlows(context)),
            add(CreateTokens(context)),
            add(CreateTokenFlow(context)),
        )
    }

    fun execute(tasks: List<String>) {
        log.info("Executing tasks: ${tasks.joinToString(", ")}")

        if(tasks.isEmpty()){
            log.info("Not tasks specified")
            return
        }

        tasks.forEach {
            val task = taskList[it]
            checkNotNull(task) { "Failed to find task '${it}'" }

            log.info("Starting task '${task.javaClass.simpleName}'...")
            task.execute()
            log.info("Task '${task.javaClass.simpleName}' completed.")
        }
    }

    private inline fun <reified T> add(task: T): Pair<String, T> where T : Task {
        return T::class.java.simpleName to task
    }
}
