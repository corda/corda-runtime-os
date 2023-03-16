package net.corda.lifecycle.impl

import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.TimerEvent
import net.corda.lifecycle.impl.LifecycleProcessor.Companion.ERRORED_REASON
import net.corda.lifecycle.impl.LifecycleProcessor.Companion.STARTED_REASON
import net.corda.lifecycle.impl.LifecycleProcessor.Companion.STOPPED_REASON
import net.corda.lifecycle.impl.registry.LifecycleRegistryCoordinatorAccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.concurrent.Delayed
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class LifecycleProcessorTest {

    companion object {
        private val NAME = LifecycleCoordinatorName.forComponent<LifecycleProcessorTest>()

        private const val REASON = "Test status update"
    }

    @Test
    fun `events are processed in delivery order`() {
        val state = LifecycleStateManager(5)
        val expectedEvents = listOf(TestEvent1, TestEvent2, TestEvent3)
        val processedEvents = mutableListOf<LifecycleEvent>()
        val processor = LifecycleProcessor(NAME, state, mock(), mock()) { event, _ ->
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
        val processor = LifecycleProcessor(NAME, state, mock(), mock()) { event, _ ->
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
        val processor = LifecycleProcessor(NAME, state, mock(), mock()) { _, _ ->
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
        val processor = LifecycleProcessor(NAME, state, mock(), mock()) { _, _ ->
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
        val processor = LifecycleProcessor(NAME, state, mock(), mock()) { event, _ ->
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

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `a timer event for a cancelled timer is not delivered to user code`(running: Boolean) {
        val state = LifecycleStateManager(5)
        state.isRunning = running
        var processedEvents = 0
        val processor = LifecycleProcessor(NAME, state, mock(), mock()) { _, _ ->
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
    fun `timers are not set up if the coordinator is not running`() {
        val state = LifecycleStateManager(5)
        var processedEvents = 0
        val processor = LifecycleProcessor(NAME, state, mock(), mock()) { _, _ ->
            processedEvents++
        }
        val key = "my_key"
        state.postEvent(SetUpTimer(key, 100L) { TestTimerEvent(key) })
        process(processor)
        assertFalse(state.isTimerRunning(key))
    }

    @Test
    fun `batching delivers the right number of events to the processor`() {
        val state = LifecycleStateManager(2)
        state.isRunning = true
        val processedEvents = mutableListOf<LifecycleEvent>()
        val processor = LifecycleProcessor(NAME, state, mock(), mock()) { event, _ ->
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
    @Suppress("TooGenericExceptionThrown")
    fun `when an unhandled error is processed by the user the batch is marked as failed`() {
        val state = LifecycleStateManager(5)
        state.isRunning = true
        var processedErrors = 0
        val processor = LifecycleProcessor(NAME, state, mock(), mock()) { event, _ ->
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
    @Suppress("TooGenericExceptionThrown")
    fun `when a handled error is processed by the user the batch is marked as succeeded`() {
        val state = LifecycleStateManager(5)
        state.isRunning = true
        var processedErrors = 0
        val processor = LifecycleProcessor(NAME, state, mock(), mock()) { event, _ ->
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
    @Suppress("TooGenericExceptionThrown")
    fun `when the error handler throws another error the batch is marked as failed`() {
        val state = LifecycleStateManager(5)
        state.isRunning = true
        var processedErrors = 0
        val processor = LifecycleProcessor(NAME, state, mock(), mock()) { event, _ ->
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
    @Suppress("TooGenericExceptionThrown")
    fun `in a failed batch the remainder of the batch is processed after the failure`() {
        val state = LifecycleStateManager(5)
        state.isRunning = true
        var processedErrors = 0
        var processedExtraEvents = false
        val processor = LifecycleProcessor(NAME, state, mock(), mock()) { event, _ ->
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

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `adding a new registration updates the state and posts the current status`(running: Boolean) {
        val state = LifecycleStateManager(5)
        state.isRunning = running
        var processedEvents = 0
        val processor = LifecycleProcessor(NAME, state, mock(), mock()) { _, _ ->
            processedEvents++
        }
        val registration = mock<Registration>()
        val coordinator = mock<LifecycleCoordinatorInternal>()
        state.status = LifecycleStatus.DOWN
        state.postEvent(NewRegistration(registration))
        process(processor, coordinator = coordinator)
        verify(registration).updateCoordinatorStatus(coordinator, LifecycleStatus.DOWN)
        assertThat(state.registrations).containsExactly(registration)
        assertEquals(0, processedEvents)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `removing a registration updates the state`(running: Boolean) {
        val state = LifecycleStateManager(5)
        state.isRunning = running
        var processedEvents = 0
        val processor = LifecycleProcessor(NAME, state, mock(), mock()) { _, _ ->
            processedEvents++
        }
        val registration = mock<Registration>()
        val coordinator = mock<LifecycleCoordinatorInternal>()
        state.status = LifecycleStatus.DOWN
        state.registrations.add(registration)
        state.postEvent(CancelRegistration(registration))
        process(processor, coordinator = coordinator)
        assertThat(state.registrations).isEmpty()
        assertEquals(0, processedEvents)
    }

    @Test
    fun `an event for an updated status is delivered to all current registrations`() {
        val state = LifecycleStateManager(5)
        state.isRunning = true
        var processedEvents = 0
        val registry = mock<LifecycleRegistryCoordinatorAccess>()
        val processor = LifecycleProcessor(NAME, state, registry, mock()) { _, _ ->
            processedEvents++
        }
        val registration1 = mock<Registration>()
        val registration2 = mock<Registration>()
        val coordinator = setupCoordinatorMock()
        state.status = LifecycleStatus.DOWN
        state.registrations.add(registration1)
        state.registrations.add(registration2)
        state.postEvent(StatusChange(LifecycleStatus.UP, REASON))
        process(processor, coordinator = coordinator)
        verify(registration1).updateCoordinatorStatus(coordinator, LifecycleStatus.UP)
        verify(registration2).updateCoordinatorStatus(coordinator, LifecycleStatus.UP)
        verify(registry).updateStatus(NAME, LifecycleStatus.UP, REASON)
        assertEquals(0, processedEvents)
    }

    @Test
    fun `status update events are not delivered to the registration if the coordinator is stopped`() {
        val state = LifecycleStateManager(5)
        var processedEvents = 0
        val registry = mock<LifecycleRegistryCoordinatorAccess>()
        val processor = LifecycleProcessor(NAME, state, registry, mock()) { _, _ ->
            processedEvents++
        }
        val registration1 = mock<Registration>()
        val registration2 = mock<Registration>()
        listOf(registration1, registration2).forEach { state.registrations.add(it) }
        state.postEvent(StatusChange(LifecycleStatus.UP, REASON))
        process(processor)
        verify(registration1, never()).updateCoordinatorStatus(any(), any())
        verify(registration2, never()).updateCoordinatorStatus(any(), any())
        verify(registry, never()).updateStatus(any(), any(), any())
        assertEquals(0, processedEvents)
    }

    @Test
    fun `stop event causes the coordinator status to be set to down`() {
        val state = LifecycleStateManager(5)
        state.isRunning = true
        var processedStopEvents = 0
        var processedOtherEvents = 0
        val registry = mock<LifecycleRegistryCoordinatorAccess>()
        val processor = LifecycleProcessor(NAME, state, registry, mock()) { event, _ ->
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
        val coordinator = setupCoordinatorMock()
        state.status = LifecycleStatus.UP
        listOf(registration1, registration2).forEach { state.registrations.add(it) }
        state.postEvent(StopEvent())
        process(processor, coordinator = coordinator)
        assertEquals(LifecycleStatus.DOWN, state.status)
        verify(registration1).updateCoordinatorStatus(coordinator, LifecycleStatus.DOWN)
        verify(registration2).updateCoordinatorStatus(coordinator, LifecycleStatus.DOWN)
        verify(registry).updateStatus(NAME, LifecycleStatus.DOWN, STOPPED_REASON)
        assertEquals(1, processedStopEvents)
        assertEquals(0, processedOtherEvents)
    }

    @Test
    fun `start event causes a request to notify about all current tracked registrations`() {
        val state = LifecycleStateManager(5)
        var processedStartEvents = 0
        var processedOtherEvents = 0
        val registry = mock<LifecycleRegistryCoordinatorAccess>()
        val processor = LifecycleProcessor(NAME, state, registry, mock()) { event, _ ->
            when (event) {
                is StartEvent -> {
                    processedStartEvents++
                }
                else -> {
                    processedOtherEvents++
                }
            }
        }
        val registration1 = mock<Registration>()
        val registration2 = mock<Registration>()
        val registered1 = mock<Registration>()
        val registered2 = mock<Registration>()
        val coordinator = setupCoordinatorMock()
        listOf(registration1, registration2).forEach { state.trackedRegistrations.add(it) }
        listOf(registered1, registered2).forEach { state.registrations.add(it) }
        state.postEvent(StartEvent())
        process(processor, coordinator = coordinator)
        verify(registration1).notifyCurrentStatus()
        verify(registration2).notifyCurrentStatus()
        verify(registered1).updateCoordinatorStatus(coordinator, LifecycleStatus.DOWN)
        verify(registered2).updateCoordinatorStatus(coordinator, LifecycleStatus.DOWN)
        verify(registry).updateStatus(NAME, LifecycleStatus.DOWN, STARTED_REASON)
        assertEquals(1, processedStartEvents)
        assertEquals(0, processedOtherEvents)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `adding a tracked registration updates the state`(running: Boolean) {
        val state = LifecycleStateManager(5)
        var processedEvents = 0
        val processor = LifecycleProcessor(NAME, state, mock(), mock()) { _, _ ->
            processedEvents++
        }
        val registration = mock<Registration>()
        state.isRunning = running
        state.postEvent(TrackRegistration(registration))
        process(processor)
        assertThat(state.trackedRegistrations).containsExactly(registration)
        assertEquals(0, processedEvents)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `removing a tracked registration updates the state`(running: Boolean) {
        val state = LifecycleStateManager(5)
        var processedEvents = 0
        val processor = LifecycleProcessor(NAME, state, mock(), mock()) { _, _ ->
            processedEvents++
        }
        val registration = mock<Registration>()
        state.isRunning = running
        state.trackedRegistrations.add(registration)
        state.postEvent(StopTrackingRegistration(registration))
        process(processor)
        assertThat(state.registrations).isEmpty()
    }

    @Test
    fun `stop due to an error results in the status being set to error`() {
        val state = LifecycleStateManager(5)
        var processedStopEvents = 0
        val registry = mock<LifecycleRegistryCoordinatorAccess>()
        val processor = LifecycleProcessor(NAME, state, registry, mock()) { event, _ ->
            when (event) {
                is StopEvent -> {
                    processedStopEvents++
                }
            }
        }
        state.isRunning = true
        state.postEvent(StopEvent(errored = true))
        val registration = mock<Registration>()
        val coordinator = setupCoordinatorMock()
        state.registrations.add(registration)
        process(processor, coordinator = coordinator)
        assertEquals(LifecycleStatus.ERROR, state.status)
        verify(registration).updateCoordinatorStatus(coordinator, LifecycleStatus.ERROR)
        verify(registry).updateStatus(NAME, LifecycleStatus.ERROR, ERRORED_REASON)
    }

    @Test
    fun `starting from an errored state causes the status to be set back to down`() {
        val state = LifecycleStateManager(5)
        var processedStartEvents = 0
        val registry = mock<LifecycleRegistryCoordinatorAccess>()
        val processor = LifecycleProcessor(NAME, state, registry, mock()) { event, _ ->
            when (event) {
                is StartEvent -> {
                    processedStartEvents++
                }
            }
        }
        state.status = LifecycleStatus.ERROR
        val registration = mock<Registration>()
        val coordinator = setupCoordinatorMock()
        state.registrations.add(registration)
        state.postEvent(StartEvent())
        process(processor, coordinator = coordinator)
        assertEquals(LifecycleStatus.DOWN, state.status)
        verify(registration).updateCoordinatorStatus(coordinator, LifecycleStatus.DOWN)
        verify(registry).updateStatus(NAME, LifecycleStatus.DOWN, STARTED_REASON)
    }

    @Test
    fun `processing a coordinator close event causes the coordinator to be removed from the registry`() {
        val state = LifecycleStateManager(5)
        val registry = mock<LifecycleRegistryCoordinatorAccess>()
        val coordinator = setupCoordinatorMock()
        val processor = LifecycleProcessor(NAME, state, registry, mock()) { _, _ -> }
        state.postEvent(CloseCoordinator())
        process(processor, coordinator = coordinator)
        assertEquals(LifecycleStatus.DOWN, state.status)
    }

    @Test
    fun `all managed resources are closed when requested`() {
        val state = LifecycleStateManager(5)
        val registry = mock<LifecycleRegistryCoordinatorAccess>()
        val processor = LifecycleProcessor(NAME, state, registry, mock()) { _, _ -> }
        val resource1 = mock<Resource>()
        val resource2 = mock<Resource>()
        val resource3 = mock<Resource>()

        processor.addManagedResource<Resource>("TEST1") { resource1 }
        processor.addManagedResource<Resource>("TEST2") { resource2 }
        processor.addManagedResource<Resource>("TEST3") { resource3 }

        processor.closeManagedResources(null)
        verify(resource1).close()
        verify(resource2).close()
        verify(resource3).close()
    }

    @Test
    fun `only requested managed resources are closed`() {
        val state = LifecycleStateManager(5)
        val registry = mock<LifecycleRegistryCoordinatorAccess>()
        val processor = LifecycleProcessor(NAME, state, registry, mock()) { _, _ -> }
        val resource1 = mock<Resource>()
        val resource2 = mock<Resource>()
        val resource3 = mock<Resource>()

        processor.addManagedResource<Resource>("TEST1") { resource1 }
        processor.addManagedResource<Resource>("TEST2") { resource2 }
        processor.addManagedResource<Resource>("TEST3") { resource3 }

        processor.closeManagedResources(setOf("TEST1", "TEST2"))

        verify(resource1).close()
        verify(resource2).close()
        verify(resource3, never()).close()
    }


    @Test
    fun `managed resources are closed only once`() {
        val state = LifecycleStateManager(5)
        val registry = mock<LifecycleRegistryCoordinatorAccess>()
        val processor = LifecycleProcessor(NAME, state, registry, mock()) { _, _ -> }
        val resource = mock<Resource>()
        processor.addManagedResource<Resource>("TEST") { resource }
        processor.closeManagedResources(setOf("TEST"))

        processor.closeManagedResources(setOf("TEST"))

        verify(resource, times(1)).close()
    }

    @Test
    fun `managed resources are closed when new object of same name created`() {
        val state = LifecycleStateManager(5)
        val registry = mock<LifecycleRegistryCoordinatorAccess>()
        val processor = LifecycleProcessor(NAME, state, registry, mock()) { _, _ -> }
        val resource1 = mock<Resource>()
        val resource2 = mock<Resource>()

        processor.addManagedResource<Resource>("TEST") { resource1 }
        processor.addManagedResource<Resource>("TEST") { resource2 }

        verify(resource1).close()
    }

    @Test
    fun `addManagedResource will return the created resource`() {
        val state = LifecycleStateManager(5)
        val registry = mock<LifecycleRegistryCoordinatorAccess>()
        val processor = LifecycleProcessor(NAME, state, registry, mock()) { _, _ -> }
        val createdResource = mock<Resource>()

        val returnedResource = processor.addManagedResource<Resource>("TEST") { createdResource }

        assertThat(returnedResource).isSameAs(createdResource)
    }

    @Test
    fun `dependent components are registered on start`() {
        val state = LifecycleStateManager(5)
        val registry = mock<LifecycleRegistryCoordinatorAccess>()
        val dependentComponents = mock<DependentComponents>()
        val processor = LifecycleProcessor(NAME, state, registry, dependentComponents) { _, _ -> }

        state.postEvent(StartEvent())
        process(processor)

        verify(dependentComponents).registerAndStartAll(any())
    }

    @Test
    fun `dependent components are stopped on stop - no error`() {
        val state = LifecycleStateManager(5)
        val registry = mock<LifecycleRegistryCoordinatorAccess>()
        val dependentComponents = mock<DependentComponents>()
        val processor = LifecycleProcessor(NAME, state, registry, dependentComponents) { _, _ -> }

        state.postEvent(StartEvent())
        state.postEvent(StopEvent())
        process(processor)

        verify(dependentComponents).stopAll()
    }

    @Test
    fun `dependent components are not stopped on stop - error`() {
        val state = LifecycleStateManager(5)
        val registry = mock<LifecycleRegistryCoordinatorAccess>()
        val dependentComponents = mock<DependentComponents>()
        val processor = LifecycleProcessor(NAME, state, registry, dependentComponents) { _, _ -> }

        state.postEvent(StartEvent())
        state.postEvent(StopEvent(errored = true))
        process(processor)

        verify(dependentComponents, never()).stopAll()
    }

    @Test
    fun `processClose will close all the timers`() {
        val state = mock<LifecycleStateManager> {
            on { nextBatch() } doReturn listOf(CloseCoordinator())
        }
        val processor = LifecycleProcessor(NAME, state, mock(), mock(), mock())

        processor.processEvents(mock(), mock())

        verify(state).cancelAllTimer()
    }

    private object TestEvent1 : LifecycleEvent
    private object TestEvent2 : LifecycleEvent
    private object TestEvent3 : LifecycleEvent

    private class TestTimerEvent(override val key: String) : TimerEvent

    private fun process(
        processor: LifecycleProcessor,
        shouldSucceed: Boolean = true,
        coordinator: LifecycleCoordinatorInternal = mock()
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

    private fun setupCoordinatorMock(): LifecycleCoordinatorInternal {
        val coordinator = mock<LifecycleCoordinatorInternal>()
        doReturn(NAME).`when`(coordinator).name
        return coordinator
    }
}
