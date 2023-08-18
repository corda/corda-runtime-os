package net.corda.applications.workers.workercommon

interface WorkerWebServer<T> {
    fun getServer(): T
}