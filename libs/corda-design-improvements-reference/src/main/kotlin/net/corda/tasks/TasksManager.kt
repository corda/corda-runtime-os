package net.corda.tasks

import java.util.concurrent.Future
import java.util.concurrent.FutureTask

interface TasksManager {
    fun getLongRunningTasks(): List<FutureTask<Any>>
    fun getCurrentShortRunningTasks(): List<Future<Any>>
}