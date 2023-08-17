package net.corda.libs.scheduler.datamodel

import net.corda.libs.scheduler.datamodel.db.TaskSchedulerLogEntityRepository
import javax.persistence.EntityManagerFactory

class SchedulerLogImpl(
    private val emf: EntityManagerFactory,
    private val logEntityRepository: TaskSchedulerLogEntityRepository = TaskSchedulerLogEntityRepository()
) : SchedulerLog {

    override fun getLastTriggerAndLock(taskName: String, schedulerId: String) : SchedulerLock {
        return SchedulerLockImpl(taskName, schedulerId, emf.createEntityManager(), logEntityRepository)
    }
}