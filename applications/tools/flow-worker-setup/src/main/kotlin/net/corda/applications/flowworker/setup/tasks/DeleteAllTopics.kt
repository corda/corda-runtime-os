package net.corda.applications.flowworker.setup.tasks

import net.corda.applications.flowworker.setup.Task
import net.corda.applications.flowworker.setup.TaskContext

class DeleteAllTopics(private val context: TaskContext): Task {

    override fun execute(){
        context.deleteAllTopics()
    }
}