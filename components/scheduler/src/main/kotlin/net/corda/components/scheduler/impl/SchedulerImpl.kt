package net.corda.components.scheduler.impl

import net.corda.components.scheduler.Schedule
import net.corda.components.scheduler.Scheduler
import net.corda.components.scheduler.TriggerPublisher
import net.corda.libs.scheduler.datamodel.SchedulerLog
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName

// NOTE: the implementation of Scheduler is heavily based on the Reconciler design.
//  In the future, we should consider using the scheduler as part of reconciliation.
class SchedulerImpl(
    override val schedule: Schedule,
    publisher: TriggerPublisher,
    schedulerLog: SchedulerLog,
    coordinatorFactory: LifecycleCoordinatorFactory,
)  : Scheduler {

    private val coordinator =
        coordinatorFactory.createCoordinator(
            LifecycleCoordinatorName("scheduler-${schedule.taskName}"),
            SchedulerEventHandler(
                schedule,
                publisher,
                schedulerLog
            )
        )

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}