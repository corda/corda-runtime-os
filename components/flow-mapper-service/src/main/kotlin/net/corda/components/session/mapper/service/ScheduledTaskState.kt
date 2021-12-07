package net.corda.components.session.mapper.service

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
        executorService.shutdown()
        publisher?.close()
    }
}
