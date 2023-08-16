package net.corda.libs.scheduler.datamodel

import net.corda.libs.scheduler.datamodel.db.internal.TaskSchedulerLogEntity

object SchedulerEntities {
    val classes = setOf(
        TaskSchedulerLogEntity::class.java,
    )
}