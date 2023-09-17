package net.corda.components.scheduler

import net.corda.components.scheduler.impl.SchedulerEventHandler
import net.corda.components.scheduler.impl.SchedulerImpl
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class SchedulerTest {
    @Test
    fun `when scheduler created create coordinator`() {
        val schedule = Schedule("batman", 2000, "superman")
        val coordinator = mock< LifecycleCoordinatorFactory>()
        SchedulerImpl(schedule, mock(), mock(), coordinator)
        verify(coordinator).createCoordinator(
            argThat { name: LifecycleCoordinatorName -> name.componentName == "scheduler-batman" },
            isA<SchedulerEventHandler>()
        )
    }
}