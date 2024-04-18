package net.corda.libs.scheduler.datamodel.db

import net.corda.libs.scheduler.datamodel.db.internal.TASK_SCHEDULER_LOG_GET_QUERY_NAME
import net.corda.libs.scheduler.datamodel.db.internal.TASK_SCHEDULER_LOG_QUERY_PARAM_NAME
import net.corda.libs.scheduler.datamodel.db.internal.TASK_SCHEDULER_LOG_QUERY_PARAM_SCHEDULER_ID
import net.corda.libs.scheduler.datamodel.db.internal.TASK_SCHEDULER_LOG_UPDATE_QUERY_NAME
import net.corda.libs.scheduler.datamodel.db.internal.TaskSchedulerLogEntity
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.Date
import javax.persistence.EntityManager
import javax.persistence.LockModeType
import javax.persistence.PersistenceException

/**
 * Abstraction of the named queries.
 */
class TaskSchedulerLogEntityRepository {
    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    /**
     * Get the latest log for a given `taskName`, or initialise a new one if one doesn't exist.
     */
    fun getOrInitialiseLog(taskName: String, schedulerId: String, em: EntityManager) : TaskSchedulerLog {
        val readQuery = em.createNamedQuery(TASK_SCHEDULER_LOG_GET_QUERY_NAME, TaskSchedulerLogEntity::class.java)
        readQuery.setParameter(TASK_SCHEDULER_LOG_QUERY_PARAM_NAME, taskName)
        readQuery.lockMode = LockModeType.PESSIMISTIC_WRITE
        return readQuery.resultList.singleOrNull()?:
            // try to persist, but catch a constraint violation caused by a possible race condition
            try {
                TaskSchedulerLogEntity(taskName, schedulerId, Instant.MIN, Date.from(Instant.now())).also {
                    em.merge(it)
                    em.flush()
                }
            }
            catch (e: PersistenceException) {
                val testQuery = em.createNamedQuery(TASK_SCHEDULER_LOG_GET_QUERY_NAME, TaskSchedulerLogEntity::class.java)
                testQuery.setParameter(TASK_SCHEDULER_LOG_QUERY_PARAM_NAME, taskName)
                if(testQuery.resultList.size == 1) {
                    // in this case, re-run the get query
                    log.warn("Race condition on inserting scheduled task. Ignoring the exception and returning the existing value: $e")
                    readQuery.resultList.first()
                } else {
                    throw e
                }
            }
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

