package net.corda.session.mapper.service.executor

import net.corda.messaging.api.publisher.Publisher
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture

data class ScheduledTaskState(
    val executorService: ScheduledExecutorService,
    val publisher: Publisher?,
    val tasks: MutableMap<String, ScheduledFuture<*>>
) :AutoCloseable {
    override fun close() {
        tasks.values.forEach { it.cancel(false) }
        tasks.clear()
        executorService.shutdown()
        publisher?.close()
    }
}
