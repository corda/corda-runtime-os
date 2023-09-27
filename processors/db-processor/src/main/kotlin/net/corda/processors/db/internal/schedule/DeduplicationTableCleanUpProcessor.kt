package net.corda.processors.db.internal.schedule

import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.virtualnode.datamodel.repository.RequestsIdsRepository
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.orm.JpaEntitiesSet
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
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

        // Need to merge with `SchedulerProcessorImpl.DEDUPLICATION_TABLE_CLEAN_UP_TASK` but there isn't currently a good candidate module
        private const val DEDUPLICATION_TABLE_CLEAN_UP_TASK = "deduplication-table-clean-up-task"
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
                if (taskName == DEDUPLICATION_TABLE_CLEAN_UP_TASK) {
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
            dbConnectionManager.createEntityManagerFactory(
                virtualNodeInfo.vaultDmlConnectionId,
                // We don't really want to make use of any entities here.
                object : JpaEntitiesSet {
                    override val persistenceUnitName: String
                        get() = ""
                    override val classes: Set<Class<*>>
                        get() = emptySet()
                }
            ).use { emf ->
                emf.createEntityManager().transaction { em ->
                    // TODO The below interval needs to be made configurable
                    requestsIdsRepository.deleteRequestsOlderThan(120, em)
                }
            }
        } catch (e: Exception) {
            log.warn("Cleaning up deduplication table for vnode: ${virtualNodeInfo.holdingIdentity.shortHash} FAILED", e)
        }
    }
}