package net.corda.libs.scheduler.datamodel.db.internal

import java.time.Instant
import java.util.Date
import javax.persistence.EntityManager
import javax.persistence.LockModeType

/**
 * Abstraction of the named queries.
 */
class TaskSchedulerLogEntityRepository {
    /**
     * Get the latest log for a given `taskName`, or initialise a new one if one doesn't exist.
     */
    fun getOrInitialiseLog(taskName: String, schedulerId: String, em: EntityManager) : TaskSchedulerLogEntity {
        val readQuery = em.createNamedQuery(TASK_SCHEDULER_LOG_GET_QUERY_NAME, TaskSchedulerLogEntity::class.java)
        readQuery.setParameter(TASK_SCHEDULER_LOG_QUERY_PARAM_NAME, taskName)
        readQuery.lockMode = LockModeType.PESSIMISTIC_WRITE
        return readQuery.resultList.singleOrNull()?:TaskSchedulerLogEntity(taskName, schedulerId, Instant.MIN, Date.from(
            Instant.now()))
    }

    /**
     * Update the log for `taskName`.
     */
    fun updateLog(taskName: String, schedulerId: String, em: EntityManager) {
        val updateQ = em.createNamedQuery(TASK_SCHEDULER_LOG_UPDATE_QUERY_NAME)
        updateQ.setParameter(TASK_SCHEDULER_LOG_QUERY_PARAM_NAME, taskName)
        updateQ.setParameter(TASK_SCHEDULER_LOG_QUERY_PARAM_SCHEDULER_ID, schedulerId)
        updateQ.executeUpdate()
    }
}