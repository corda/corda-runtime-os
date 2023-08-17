package net.corda.components.scheduler.impl

import net.corda.components.scheduler.Schedule
import net.corda.components.scheduler.Scheduler
import net.corda.components.scheduler.SchedulerFactory
import net.corda.components.scheduler.TriggerPublisher
import net.corda.libs.scheduler.datamodel.SchedulerLog
import net.corda.lifecycle.LifecycleCoordinatorFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference


@Component(service = [SchedulerFactory::class])
class SchedulerFactoryImpl @Activate constructor(
    @Reference(service = TriggerPublisher::class)
    private val triggerPublisher: TriggerPublisher,
    @Reference(service = SchedulerLog::class)
    private val schedulerLog: SchedulerLog,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,

    ) : SchedulerFactory {
    override fun create(schedule: Schedule): Scheduler =
        SchedulerImpl(schedule, triggerPublisher, schedulerLog, coordinatorFactory)
}