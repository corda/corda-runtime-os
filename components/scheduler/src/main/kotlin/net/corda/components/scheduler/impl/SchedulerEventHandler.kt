package net.corda.components.scheduler.impl

import net.corda.components.scheduler.Schedule
import net.corda.components.scheduler.TriggerPublisher
import net.corda.libs.scheduler.datamodel.SchedulerLog
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.TimerEvent
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.net.InetAddress
import kotlin.math.min

class SchedulerEventHandler(
    private val schedule: Schedule,
    private val publisher: TriggerPublisher,
    private val schedulerLog: SchedulerLog,
    private val schedulerName: String =
        System.getenv("HOSTNAME")?:
        InetAddress.getLocalHost()?.canonicalHostName?:
        System.getenv("COMPUTERNAME")?:
        "<UNKNOWN>"
    ) : LifecycleEventHandler {

    private val name = "${SchedulerEventHandler::class.java.name}-${schedule.taskName}"
    private val logger = LoggerFactory.getLogger(name)

    private var registration: RegistrationHandle? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            is ScheduleEvent -> triggerAndScheduleNext(coordinator)
            is StopEvent -> onStopEvent(coordinator)
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        registration?.close()
        registration = coordinator.followStatusChangesByName(
            setOf(
                publisher.lifecycleCoordinatorName
            )
        )
    }

    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        if (event.status == LifecycleStatus.UP) {
            logger.info("Starting task scheduling")
            coordinator.updateStatus(LifecycleStatus.UP)
            triggerAndScheduleNext(coordinator)
        } else {
            coordinator.updateStatus(event.status)
            coordinator.cancelTimer(name)
        }
    }

    private fun triggerAndScheduleNext(coordinator: LifecycleCoordinator) = try {
        schedulerLog.getLastTriggerAndLock(schedule.taskName, schedulerName).use { schedulerLock ->
            if (schedulerLock.secondsSinceLastScheduledTrigger >= schedule.scheduleIntervalInSeconds) {
                publisher.publish(schedule.taskName, schedule.scheduleTriggerTopic)
                schedulerLock.updateLog(schedulerName)
            } else {
                logger.debug { "Skipping publishing task scheduler for ${schedule.taskName} " +
                        "because it has only been ${schedulerLock.secondsSinceLastScheduledTrigger} " +
                        "since the last trigger." }
            }
        }
        scheduleNext(coordinator)
    } catch (e: Throwable) {
        logger.warn("Task scheduling for ${schedule.taskName} failed. Terminating Scheduler", e)
        coordinator.updateStatus(LifecycleStatus.DOWN)
    }

    private fun scheduleNext(coordinator: LifecycleCoordinator) {
        // NOTE: setting timer interval to half of the schedule interval,
        //  and never longer than 2 mins so that we don't leave too long to
        //  recover in case of a failure.
        val timerInterval = min(schedule.scheduleIntervalInSeconds * 1000 / 2, 2*60*1000)
        coordinator.setTimer(name, timerInterval) { ScheduleEvent(it) }
    }

    private fun onStopEvent(coordinator: LifecycleCoordinator) {
        coordinator.cancelTimer(name)
        closeResources()
    }

    private fun closeResources() {
        registration?.close()
        registration = null
    }

    internal data class ScheduleEvent(override val key: String) : TimerEvent
}