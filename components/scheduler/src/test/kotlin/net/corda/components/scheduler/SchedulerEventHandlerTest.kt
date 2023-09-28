package net.corda.components.scheduler

import net.corda.components.scheduler.impl.SchedulerEventHandler
import net.corda.libs.scheduler.datamodel.SchedulerLock
import net.corda.libs.scheduler.datamodel.SchedulerLog
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SchedulerEventHandlerTest {
    private val schedulerName = "superman"
    private val schedule =
        Schedule("batman", 113, "Nananana")
    private val publisher = mock<TriggerPublisher> {
        on { lifecycleCoordinatorName } doReturn LifecycleCoordinatorName("foo")
    }
    private val schedulerLock = mock<SchedulerLock> {
        on { secondsSinceLastScheduledTrigger } doReturn 115
    }
    private val schedulerLog = mock<SchedulerLog>() {
        on { getLastTriggerAndLock(any(), any()) } doReturn schedulerLock
    }
    private val coordinator = mock<LifecycleCoordinator>()

    @Test
    fun `when ScheduleEvent publish and update DB log entry and schedule again`() {
        val handler = SchedulerEventHandler(schedule, publisher, schedulerLog, schedulerName)
        val name = "${SchedulerEventHandler::class.java.name}-${schedule.taskName}"
        handler.processEvent(SchedulerEventHandler.ScheduleEvent(name), coordinator)

        verify(schedulerLog).getLastTriggerAndLock(schedule.taskName, schedulerName)
        verify(publisher).publish(schedule.taskName, schedule.scheduleTriggerTopic)
        verify(schedulerLock).updateLog(schedulerName)
        verify(coordinator).setTimer(
            eq(name),
            eq(schedule.scheduleIntervalInSeconds * 1000 / 2),
            any()
        )
    }

    @Test
    fun `when ScheduleEvent don't schedule if too soon`() {
        val schedule =
            Schedule("hulk", 1230, "Nananana")
        val handler = SchedulerEventHandler(schedule, publisher, schedulerLog, schedulerName)
        val name = "${SchedulerEventHandler::class.java.name}-${schedule.taskName}"
        handler.processEvent(SchedulerEventHandler.ScheduleEvent(name), coordinator)

        verify(publisher, times(0)).publish(any(), any())
        verify(schedulerLock, times(0)).updateLog(schedulerName)
    }

    @Test
    fun `when ScheduleEvent never wait longer than 2 mins to reschedule`() {
        val schedule =
            Schedule("hulk", 5*60, "Nananana")
        val handler = SchedulerEventHandler(schedule, publisher, schedulerLog, schedulerName)
        val name = "${SchedulerEventHandler::class.java.name}-${schedule.taskName}"
        handler.processEvent(SchedulerEventHandler.ScheduleEvent(name), coordinator)

        verify(coordinator).setTimer(
            eq(name),
            eq(2*60*1000),
            any()
        )
    }

    @Test
    fun `when StartEvent follow publisher status`() {
        val handler = SchedulerEventHandler(schedule, publisher, schedulerLog, schedulerName)

        handler.processEvent(StartEvent(), coordinator)

        verify(coordinator).followStatusChangesByName(
            argThat {
                    names: Set<LifecycleCoordinatorName> ->
                names.contains(LifecycleCoordinatorName("foo"))
        })
    }

    @Test
    fun `when RegistrationStatusChangeEvent UP set status`() {
        val handler = SchedulerEventHandler(schedule, publisher, schedulerLog, schedulerName)

        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)

        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `when RegistrationStatusChangeEvent UP always trigger first event and schedule next in half the interval`() {
        val handler = SchedulerEventHandler(schedule, publisher, schedulerLog, schedulerName)

        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)

        verify(schedulerLog).getLastTriggerAndLock(schedule.taskName, schedulerName)
        verify(coordinator).setTimer(
            eq("${SchedulerEventHandler::class.java.name}-${schedule.taskName}"),
            eq(schedule.scheduleIntervalInSeconds * 1000 / 2),
            any()
        )
    }

    @Test
    fun `when RegistrationStatusChangeEvent DOWN set status and cancel timer`() {
        val handler = SchedulerEventHandler(schedule, publisher, schedulerLog, schedulerName)

        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), coordinator)

        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
        verify(coordinator).cancelTimer("${SchedulerEventHandler::class.java.name}-${schedule.taskName}")
    }

    @Test
    fun `when StopEvent cancel timer`() {
        val handler = SchedulerEventHandler(schedule, publisher, schedulerLog, schedulerName)

        handler.processEvent(StopEvent(), coordinator)

        verify(coordinator).cancelTimer("${SchedulerEventHandler::class.java.name}-${schedule.taskName}")
    }

    @Test
    fun `on triggerAndScheduleNext error takes coordinator to ERROR`() {
        val schedulerLock = mock<SchedulerLock>().also {
            whenever(it.secondsSinceLastScheduledTrigger).thenReturn(115)
            whenever(it.updateLog(schedulerName)).thenThrow(RuntimeException())
        }
        val schedulerLog = mock<SchedulerLog> {
            on { getLastTriggerAndLock(any(), any()) } doReturn schedulerLock
        }

        val handler = SchedulerEventHandler(schedule, publisher,  schedulerLog, schedulerName)
        handler.processEvent(SchedulerEventHandler.ScheduleEvent(""), coordinator)
        verify(coordinator, times(1)).updateStatus(LifecycleStatus.ERROR)
    }
}