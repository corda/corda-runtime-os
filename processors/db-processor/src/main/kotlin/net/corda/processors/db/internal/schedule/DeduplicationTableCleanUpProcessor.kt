package net.corda.processors.db.internal.schedule

import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import org.slf4j.LoggerFactory

class DeduplicationTableCleanUpProcessor : DurableProcessor<String, ScheduledTaskTrigger> {
    companion object {
        private val log = LoggerFactory.getLogger(DeduplicationTableCleanUpProcessor::class.java)
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<ScheduledTaskTrigger>
        get() = ScheduledTaskTrigger::class.java

    override fun onNext(events: List<Record<String, ScheduledTaskTrigger>>): List<Record<*, *>> {
        log.info("Processed the ScheduledTaskTrigger!")
        return emptyList()
    }
}