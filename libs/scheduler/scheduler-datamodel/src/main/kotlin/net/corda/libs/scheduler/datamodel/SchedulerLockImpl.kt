package net.corda.libs.scheduler.datamodel

import net.corda.libs.scheduler.datamodel.db.TaskSchedulerLog
import net.corda.libs.scheduler.datamodel.db.TaskSchedulerLogEntityRepository
import java.time.temporal.ChronoUnit
import javax.persistence.EntityManager

class SchedulerLockImpl(
    override val taskName: String,
    override val schedulerId: String,
    private val em: EntityManager,
    private val logEntityRepository: TaskSchedulerLogEntityRepository = TaskSchedulerLogEntityRepository()
) : SchedulerLock {
    override val secondsSinceLastScheduledTrigger: Long
    private val tx = em.transaction
    private val log : TaskSchedulerLog

    init {
        tx.begin()
        log = logEntityRepository.getOrInitialiseLog(taskName, schedulerId, em)
        secondsSinceLastScheduledTrigger = log.lastScheduled.until(log.now, ChronoUnit.SECONDS)
    }

    override fun updateLog(schedulerId: String) {
        log.schedulerId = schedulerId
        logEntityRepository.updateLog(taskName, schedulerId, em)
    }

    override fun close() {
        tx.commit()
        em.close()
    }
}