package net.corda.libs.scheduler.datamodel

import net.corda.libs.scheduler.datamodel.db.internal.TaskSchedulerLogEntity

object SchedulerEntities {
    val classes: Set<Class<*>> = setOf(
        TaskSchedulerLogEntity::class.java,
    )
}