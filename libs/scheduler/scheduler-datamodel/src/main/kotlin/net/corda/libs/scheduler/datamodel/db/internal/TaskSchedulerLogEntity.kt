package net.corda.libs.scheduler.datamodel.db.internal

import net.corda.libs.scheduler.datamodel.db.TaskSchedulerLog
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.NamedQuery
import javax.persistence.Table

const val TASK_SCHEDULER_LOG_UPDATE_QUERY_NAME = "TaskSchedulerLogEntity.update"
const val TASK_SCHEDULER_LOG_GET_QUERY_NAME = "TaskSchedulerLogEntity.get"
const val TASK_SCHEDULER_LOG_QUERY_PARAM_SCHEDULER_ID = "schedulerId"
const val TASK_SCHEDULER_LOG_QUERY_PARAM_NAME = "name"

@Entity
@Table(name = "task_scheduler_log")
@NamedQuery(
    name = TASK_SCHEDULER_LOG_UPDATE_QUERY_NAME,
    query = "UPDATE TaskSchedulerLogEntity s" +
            " SET s.schedulerId = :$TASK_SCHEDULER_LOG_QUERY_PARAM_SCHEDULER_ID," +
            " s.lastScheduled = CURRENT_TIMESTAMP" +
            " WHERE s.name = :$TASK_SCHEDULER_LOG_QUERY_PARAM_NAME"
)
@NamedQuery(
    name = TASK_SCHEDULER_LOG_GET_QUERY_NAME,
    query = "SELECT new net.corda.libs.scheduler.datamodel.db.internal." +
            "TaskSchedulerLogEntity(s.name, s.schedulerId, s.lastScheduled, CURRENT_TIMESTAMP)" +
            "FROM TaskSchedulerLogEntity s " +
            "WHERE s.name = :$TASK_SCHEDULER_LOG_QUERY_PARAM_NAME"
)
internal class TaskSchedulerLogEntity(
    @Id
    @Column(name = "task_name", nullable = false)
    override var name: String,

    @Column(name = "scheduler_id", nullable = false)
    override var schedulerId: String,

    // Updated by the DB.
    @Column(name = "last_scheduled", insertable = false, updatable = false)
    override var lastScheduled: Instant = Instant.MIN,

    // not managed by Hibernate, but still set as part of the get Query.
    // NOTE: using util.Date because that is what Hibernate maps CURRENT_TIMESTAMP to (java.sql.Timestamp).
    @Transient
    var dbNow: java.util.Date = java.util.Date(Long.MIN_VALUE)
) : TaskSchedulerLog {
    @Transient
    override var now: Instant = dbNow.toInstant()

    // equals override to support Hibernate's requirement
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TaskSchedulerLogEntity) return false

        if (name != other.name) return false

        return true
    }

    // hashCode override to support Hibernate's requirement
    override fun hashCode(): Int {
        return name.hashCode()
    }
}

