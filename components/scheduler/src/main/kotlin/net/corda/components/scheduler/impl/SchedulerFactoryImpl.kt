package net.corda.components.scheduler.impl

import net.corda.components.scheduler.Schedule
import net.corda.components.scheduler.Scheduler
import net.corda.components.scheduler.SchedulerFactory
import net.corda.components.scheduler.TriggerPublisher
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.scheduler.datamodel.SchedulerLogImpl
import net.corda.lifecycle.LifecycleCoordinatorFactory


class SchedulerFactoryImpl(
    private val triggerPublisher: TriggerPublisher,
    private val dbConnectionManager: DbConnectionManager,
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    ) : SchedulerFactory {

    private val schedulerLog by lazy {
        val emf = dbConnectionManager.getClusterEntityManagerFactory()
        SchedulerLogImpl(emf)
    }

    override fun create(schedule: Schedule): Scheduler =
        SchedulerImpl(schedule, triggerPublisher, schedulerLog, coordinatorFactory)
}