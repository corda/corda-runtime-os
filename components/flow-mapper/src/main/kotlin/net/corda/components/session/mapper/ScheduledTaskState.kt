package net.corda.components.session.mapper

import net.corda.messaging.api.publisher.Publisher
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture

/**
 * Helper class to shared scheduled jobs between listener and executor
 */
class ScheduledTaskState (
    val executorService: ScheduledExecutorService,
    val publisher: Publisher,
    val tasks: MutableMap<String, ScheduledFuture<*>>
)
