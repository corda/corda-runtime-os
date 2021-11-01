package net.corda.components.session.manager.dedup

import net.corda.messaging.api.publisher.Publisher
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture

data class DedupState(
    val maxSessionLength: Long,
    val eventTopic: String,
    val publisher: Publisher,
    val executorService: ScheduledExecutorService,
    val scheduledTasks: MutableMap<String, ScheduledFuture<*>>
)
