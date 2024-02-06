package net.corda.processors.db.internal.schedule

import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.virtualnode.datamodel.repository.RequestsIdsRepository
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.utilities.debug
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.slf4j.LoggerFactory
import java.time.Duration

class DeduplicationTableCleanUpProcessor(
    private val dbConnectionManager: DbConnectionManager,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val requestsIdsRepository: RequestsIdsRepository
) : DurableProcessor<String, ScheduledTaskTrigger> {
    companion object {
        private val log = LoggerFactory.getLogger(DeduplicationTableCleanUpProcessor::class.java)
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<ScheduledTaskTrigger>
        get() = ScheduledTaskTrigger::class.java

    override fun onNext(events: List<Record<String, ScheduledTaskTrigger>>): List<Record<*, *>> {
        // TODO Add metric around it?
        events
            .forEach {
                val taskName = it.key
                if (taskName == Schemas.ScheduledTask.SCHEDULED_TASK_NAME_DB_PROCESSOR) {
                    log.debug { "Cleaning up deduplication table for all vnodes" }
                    val startTime = System.nanoTime()
                    virtualNodeInfoReadService.getAll()
                        .forEach(::cleanUpDeduplicationTable)
                    val cleanUpTime = Duration.ofNanos(System.nanoTime() - startTime)
                    log.info("Cleaning up deduplication table for all vnodes COMPLETED in ${cleanUpTime.toMillis()} ms")
                }
            }
        // TODO Fix the response (at the minute the Scheduler ignores them)
        return emptyList()
    }

    private fun cleanUpDeduplicationTable(virtualNodeInfo: VirtualNodeInfo) {
        log.debug { "Cleaning up deduplication table for vnode: ${virtualNodeInfo.holdingIdentity.shortHash}" }
        try {
            dbConnectionManager.createDatasource(
                virtualNodeInfo.vaultDmlConnectionId, enablePool = false
            ).use { ds ->
                // TODO The below interval needs to be made configurable
                requestsIdsRepository.deleteRequestsOlderThan(120, ds)
            }
        } catch (e: Exception) {
            log.warn("Cleaning up deduplication table for vnode: ${virtualNodeInfo.holdingIdentity.shortHash} FAILED", e)
        }
    }
}