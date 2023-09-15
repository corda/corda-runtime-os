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
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.slf4j.LoggerFactory

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
        // TODO Add metric/ count time around it
        log.info("Cleaning up deduplication tables for all vnodes")
        virtualNodeInfoReadService.getAll().forEach {
            log.debug { "Cleaning up deduplication table for vnode: ${it.holdingIdentity.shortHash}" }
            val emf = dbConnectionManager.createEntityManagerFactory(
                it.vaultDmlConnectionId,
                // We don't really want to make use of any entities here.
                object : JpaEntitiesSet {
                    override val persistenceUnitName: String
                        get() = ""
                    override val classes: Set<Class<*>>
                        get() = emptySet()
                }
            )

            emf.use {
                it.createEntityManager().transaction { em ->
                    // TODO The below interval needs to be made configurable
                    requestsIdsRepository.deleteRequestsOlderThan(120, em)
                }
            }
        }

        log.info("Cleaning up deduplication tables for all vnodes COMPLETED")
        return emptyList()
    }
}