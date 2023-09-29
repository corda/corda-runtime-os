package net.corda.taskmanager

import net.corda.taskmanager.impl.TaskManagerFactoryImpl

interface TaskManagerFactory {

    companion object {
        val INSTANCE: TaskManagerFactory = TaskManagerFactoryImpl
    }

    fun createThreadPoolTaskManager(
        name: String,
        threadName: String,
        metricPrefix: String,
        threads: Int,
    ): TaskManager
}