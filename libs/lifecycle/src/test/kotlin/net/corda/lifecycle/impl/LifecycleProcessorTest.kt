package net.corda.lifecycle.impl

import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.TimerEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.concurrent.Delayed
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class LifecycleProcessorTest {

    companion object {
        private const val NAME = "Lifecycle-Processor-Test"
    }

    @Test
    fun `events are processed in delivery order`() {
        val state = LifecycleStateManager(5)
        val expectedEvents = listOf(TestEvent1, TestEvent2, TestEvent3)
        val processedEvents = mutableListOf<LifecycleEvent>()
        val processor = LifecycleProcessor(NAME, state) { event, _ ->
            processedEvents.add(event)
        }
        state.isRunning = true
        state.postEvent(TestEvent1)
        state.postEvent(TestEvent2)
        state.postEvent(TestEvent3)
        process(processor)
        assertEquals(expectedEvents, processedEvents)
    }

    @Test
    fun `processing of start and stop events results in running set correctly`() {
        val state = LifecycleStateManager(5)
        var runningOnStartDelivery = false
        var notRunningOnStopDelivery = false
        val processor = LifecycleProcessor(NAME, state) { event, _ ->
            when (event) {
                is StartEvent -> {
                    if (state.isRunning) {
                        runningOnStartDelivery = true
                    }
                }
                is StopEvent -> {
                    if (!state.isRunning) {
                        notRunningOnStopDelivery = true
                    }
                }
            }
        }
        state.postEvent(StartEvent())
        process(processor)
        assertTrue(state.isRunning)
        assertTrue(runningOnStartDelivery)
        state.postEvent(StopEvent())
        process(processor)
        assertFalse(state.isRunning)
        assertTrue(notRunningOnStopDelivery)
    }

    @Test
    fun `events processed while not running are removed and not delivered to user code`() {
        val state = LifecycleStateManager(5)
        var processedEvents = 0
        val processor = LifecycleProcessor(NAME, state) { _, _ ->
            processedEvents++
        }
        state.postEvent(TestEvent1)
        state.postEvent(TestEvent2)
        // Note that the state says not running, so should not deliver any events.
        process(processor)
        assertEquals(0, processedEvents)
        state.isRunning = true
        // Events should be removed by previous process so nothing should happen here either.
        process(processor)
        assertEquals(0, processedEvents)
    }

    @Test
    fun `setting and cancelling a timer updates the state correctly without delivering to user code`() {
        val state = LifecycleStateManager(5)
        var processedEvents = 0
        val processor = LifecycleProcessor(NAME, state) { _, _ ->
            processedEvents++
        }
        state.isRunning = true
        val key = "my key"
        val timerSetupEvent = SetUpTimer(key, 100L) {
            TestTimerEvent(key)
        }
        state.postEvent(timerSetupEvent)
        process(processor)
        assertTrue(state.isTimerRunning(key))
        val cancelTimer = CancelTimer(key)
        state.postEvent(cancelTimer)
        process(processor)
        assertFalse(state.isTimerRunning(key))
        assertEquals(0, processedEvents)
    }

    @Test
    fun `timer events are delivered if the timer is running`() {
        val state = LifecycleStateManager(5)
        state.isRunning = true
        var timerKey = "the wrong key"
        val processor = LifecycleProcessor(NAME, state) { event, _ ->
            when (event) {
                is TimerEvent -> {
                    timerKey = event.key
                }
            }
        }
        val key = "my_key"
        val event = TestTimerEvent(key)
        state.postEvent(event)
        state.setTimer(key, timerGenerator(event, 100L))
        process(processor)
        assertEquals(key, timerKey)
        assertFalse(state.isTimerRunning(key))
    }

    @Test
    fun `a timer event for a cancelled timer is not delivered to user code`() {
        val state = LifecycleStateManager(5)
        state.isRunning = true
        var processedEvents = 0
        val processor = LifecycleProcessor(NAME, state) { _, _ ->
            processedEvents++
        }
        val key = "my_key"
        val event = TestTimerEvent(key)
        state.setTimer(key, timerGenerator(event, 100L))
        // Sanity check the setup
        assertTrue(state.isTimerRunning(key))
        state.postEvent(CancelTimer(key))
        state.postEvent(event)
        process(processor)
        assertEquals(0, processedEvents)
        assertFalse(state.isTimerRunning(key))
    }

    @Test
    fun `batching delivers the right number of events to the processor`() {
        val state = LifecycleStateManager(2)
        state.isRunning = true
        val processedEvents = mutableListOf<LifecycleEvent>()
        val processor = LifecycleProcessor(NAME, state) { event, _ ->
            processedEvents.add(event)
        }
        state.postEvent(TestEvent1)
        state.postEvent(TestEvent2)
        state.postEvent(TestEvent3)
        process(processor)
        assertEquals(listOf(TestEvent1, TestEvent2), processedEvents)
        process(processor)
        assertEquals(listOf(TestEvent1, TestEvent2, TestEvent3), processedEvents)
    }

    @Test
    fun `when an unhandled error is processed by the user the batch is marked as failed`() {
        val state = LifecycleStateManager(5)
        state.isRunning = true
        var processedErrors = 0
        val processor = LifecycleProcessor(NAME, state) { event, _ ->
            when (event) {
                is TestEvent1 -> {
                    throw Exception("This didn't work")
                }
                is ErrorEvent -> {
                    processedErrors++
                }
            }
        }
        state.postEvent(TestEvent1)
        process(processor, false)
        assertEquals(1, processedErrors)
    }

    @Test
    fun `when a handled error is processed by the user the batch is marked as succeeded`() {
        val state = LifecycleStateManager(5)
        state.isRunning = true
        var processedErrors = 0
        val processor = LifecycleProcessor(NAME, state) { event, _ ->
            when (event) {
                is TestEvent1 -> {
                    throw Exception("This didn't work")
                }
                is ErrorEvent -> {
                    event.isHandled = true
                    processedErrors++
                }
            }
        }
        state.postEvent(TestEvent1)
        process(processor)
        assertEquals(1, processedErrors)
    }

    @Test
    fun `when the error handler throws another error the batch is marked as failed`() {
        val state = LifecycleStateManager(5)
        state.isRunning = true
        var processedErrors = 0
        val processor = LifecycleProcessor(NAME, state) { event, _ ->
            when (event) {
                is TestEvent1 -> {
                    throw Exception("This didn't work")
                }
                is ErrorEvent -> {
                    event.isHandled = true
                    processedErrors++
                    throw Exception("Failed in the error handler")
                }
            }
        }
        state.postEvent(TestEvent1)
        process(processor, false)
        assertEquals(1, processedErrors)
    }

    @Test
    fun `in a failed batch the remainder of the batch is processed after the failure`() {
        val state = LifecycleStateManager(5)
        state.isRunning = true
        var processedErrors = 0
        var processedExtraEvents = false
        val processor = LifecycleProcessor(NAME, state) { event, _ ->
            when (event) {
                is TestEvent1 -> {
                    throw Exception("This didn't work")
                }
                is TestEvent2 -> {
                    processedExtraEvents = true
                }
                is ErrorEvent -> {
                    processedErrors++
                }
            }
        }
        state.postEvent(TestEvent1)
        state.postEvent(TestEvent2)
        process(processor, false)
        assertEquals(1, processedErrors)
        assertTrue(processedExtraEvents)
    }

    @Test
    fun `adding a new registration updates the state and posts the current status`() {
        val state = LifecycleStateManager(5)
        state.isRunning = true
        var processedEvents = 0
        val processor = LifecycleProcessor(NAME, state) { _, _ ->
            processedEvents++
        }
        val registration = mock<Registration>()
        val coordinator = mock<LifecycleCoordinator>()
        state.status = LifecycleStatus.DOWN
        state.postEvent(NewRegistration(registration))
        process(processor, coordinator = coordinator)
        verify(registration).updateCoordinatorState(coordinator, LifecycleStatus.DOWN)
        assertEquals(setOf(registration), state.registrations)
        assertEquals(0, processedEvents)
    }

    @Test
    fun `removing a registration updates the state`() {
        val state = LifecycleStateManager(5)
        state.isRunning = true
        var processedEvents = 0
        val processor = LifecycleProcessor(NAME, state) { _, _ ->
            processedEvents++
        }
        val registration = mock<Registration>()
        val coordinator = mock<LifecycleCoordinator>()
        state.status = LifecycleStatus.DOWN
        state.registrations.add(registration)
        state.postEvent(CancelRegistration(registration))
        process(processor, coordinator = coordinator)
        assertEquals(setOf<Registration>(), state.registrations)
        assertEquals(0, processedEvents)
    }

    @Test
    fun `an event for an updated status is delivered to all current registrations`() {
        val state = LifecycleStateManager(5)
        state.isRunning = true
        var processedEvents = 0
        val processor = LifecycleProcessor(NAME, state) { _, _ ->
            processedEvents++
        }
        val registration1 = mock<Registration>()
        val registration2 = mock<Registration>()
        val coordinator = mock<LifecycleCoordinator>()
        state.status = LifecycleStatus.DOWN
        state.registrations.add(registration1)
        state.registrations.add(registration2)
        state.postEvent(StatusChange(LifecycleStatus.UP))
        process(processor, coordinator = coordinator)
        verify(registration1).updateCoordinatorState(coordinator, LifecycleStatus.UP)
        verify(registration2).updateCoordinatorState(coordinator, LifecycleStatus.UP)
        assertEquals(0, processedEvents)
    }

    @Test
    fun `stop event causes the coordinator status to be set to down`() {
        val state = LifecycleStateManager(5)
        state.isRunning = true
        var processedStopEvents = 0
        var processedOtherEvents = 0
        val processor = LifecycleProcessor(NAME, state) { event, _ ->
            when (event) {
                is StopEvent -> {
                    processedStopEvents++
                }
                else -> {
                    processedOtherEvents++
                }
            }
        }
        val registration1 = mock<Registration>()
        val registration2 = mock<Registration>()
        val coordinator = mock<LifecycleCoordinator>()
        state.status = LifecycleStatus.UP
        state.registrations.add(registration1)
        state.registrations.add(registration2)
        state.postEvent(StopEvent())
        process(processor, coordinator = coordinator)
        assertEquals(LifecycleStatus.DOWN, state.status)
        verify(registration1).updateCoordinatorState(coordinator, LifecycleStatus.DOWN)
        verify(registration2).updateCoordinatorState(coordinator, LifecycleStatus.DOWN)
        assertEquals(1, processedStopEvents)
        assertEquals(0, processedOtherEvents)
    }

    private object TestEvent1 : LifecycleEvent
    private object TestEvent2 : LifecycleEvent
    private object TestEvent3 : LifecycleEvent

    private class TestTimerEvent(override val key: String) : TimerEvent

    private fun process(
        processor: LifecycleProcessor,
        shouldSucceed: Boolean = true,
        coordinator: LifecycleCoordinator = mock()
    ) {
        val succeeded = processor.processEvents(coordinator, ::timerGenerator)
        assertEquals(shouldSucceed, succeeded)
    }

    private fun timerGenerator(timerEvent: TimerEvent, delay: Long): ScheduledFuture<*> {
        return object : ScheduledFuture<String> {
            private var isCancelled = false
            override fun compareTo(other: Delayed?): Int {
                throw NotImplementedError()
            }

            override fun getDelay(p0: TimeUnit): Long {
                return delay
            }

            override fun cancel(p0: Boolean): Boolean {
                isCancelled = true
                return true
            }

            override fun isCancelled(): Boolean {
                return isCancelled
            }

            override fun isDone(): Boolean {
                throw NotImplementedError()
            }

            override fun get(): String {
                return timerEvent.key
            }

            override fun get(p0: Long, p1: TimeUnit): String {
                throw NotImplementedError()
            }
        }
    }
}