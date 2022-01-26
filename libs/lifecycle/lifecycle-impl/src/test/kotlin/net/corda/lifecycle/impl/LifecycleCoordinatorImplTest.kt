package net.corda.lifecycle.impl

import net.corda.lifecycle.CustomEvent
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleException
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.TimerEvent
import net.corda.lifecycle.impl.LifecycleProcessor.Companion.STOPPED_REASON
import net.corda.lifecycle.impl.registry.LifecycleRegistryCoordinatorAccess
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

// These tests create simple lifecycle coordinator, provide an event handler to do some processing of events, and then
// deliver some series of events to the coordinator to process. There are some subtleties to keep in mind when adding to
// these tests:
// - The event handler runs in a different thread, and in general individual events may be handled across different
//   threads. This usually means that latches are required to ensure the main processing doesn't continue until certain
//   events have been processed (failure to do this will result in race conditions and flaky tests)
// - The coordinator catches all exceptions and redelivers them to the event handler to give the user a chance to cope
//   with errors. Unfortunately this includes test assertions. Asserting inside the event handler is therefore unlikely
//   to behave correctly in the event that the assertion fails.
internal class LifecycleCoordinatorImplTest {

    companion object {

        private const val BATCH_SIZE = 128

        private const val TIMEOUT = 500L

        private const val TIMER_DELAY = 100L

        private const val NUM_LOOPS = 5

        private const val REASON = "Test status change"
    }

    interface PostEvent : LifecycleEvent

    interface ThrowException : LifecycleEvent

    @Test
    fun burstEvents() {
        val n = BATCH_SIZE * 2
        val startLatch = CountDownLatch(1)
        val countDownLatch =
            CountDownLatch(n)  // Used to test all posted events are processed when coordinator stopped.
        val stopLatch = CountDownLatch(1)
        var eventsProcessed = 0
        var unexpectedEventCount = 0
        createCoordinator { event: LifecycleEvent, _: LifecycleCoordinator ->
            when (event) {
                is StartEvent -> {
                    startLatch.countDown()
                }
                is PostEvent -> {
                    eventsProcessed++
                    countDownLatch.countDown()
                }
                is StopEvent -> {
                    stopLatch.countDown()
                }
                else -> {
                    unexpectedEventCount++
                }
            }
        }.use { coordinator ->
            coordinator.start()
            assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            for (i in 0 until n) {
                coordinator.postEvent(object : PostEvent {})
            }
            coordinator.stop()
            assertTrue(stopLatch.await(TIMEOUT * n, TimeUnit.MILLISECONDS))
            assertTrue(countDownLatch.await(TIMEOUT * n, TimeUnit.MILLISECONDS))
            assertEquals(n, eventsProcessed)
            assertEquals(0, unexpectedEventCount)
        }
    }

    @Test
    fun burstTimers() {
        val n = BATCH_SIZE * 2
        val startLatch = CountDownLatch(1)
        val countDownLatch =
            CountDownLatch(n)  // Used to test all posted events are processed when coordinator stopped.
        val stopLatch = CountDownLatch(1)
        var unexpectedEventCount = 0
        createCoordinator { event: LifecycleEvent, _: LifecycleCoordinator ->
            when (event) {
                is StartEvent -> {
                    startLatch.countDown()
                }
                is TimerEvent -> {
                    countDownLatch.countDown()
                }
                is StopEvent -> {
                    stopLatch.countDown()

                }
                else -> {
                    unexpectedEventCount++
                }
            }
        }.use { coordinator ->
            coordinator.start()
            assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            for (i in 0 until n) {
                val onTime = object : TimerEvent {
                    override val key: String
                        get() = i.toString()
                }
                val delay = Random.nextLong(0, TIMER_DELAY)
                coordinator.setTimer(onTime.key, delay) { onTime }
            }
            Thread {
                while (countDownLatch.count >= BATCH_SIZE / 2) {
                    Thread.sleep(Random.nextLong(0, TIMER_DELAY / 20))
                }
                coordinator.stop()
            }.start()
            assertTrue(stopLatch.await(TIMEOUT * n, TimeUnit.MILLISECONDS))
        }
        assertTrue(n > countDownLatch.count)
        assertEquals(0, unexpectedEventCount)
    }

    @Test
    fun cancelTimer() {
        val startLatch = CountDownLatch(1)
        val key = "kill_me_softly"
        val timerLatch = CountDownLatch(1)
        var deliveredTimerEvents = 0
        createCoordinator { event: LifecycleEvent, _: LifecycleCoordinator ->
            when (event) {
                is StartEvent -> startLatch.countDown()
                is TimerEvent -> {
                    deliveredTimerEvents++
                }
            }
        }.use { coordinator ->
            coordinator.start()
            assertTrue(startLatch.await(TIMEOUT * 2, TimeUnit.MILLISECONDS))
            coordinator.setTimer(key, TIMER_DELAY) {
                object : TimerEvent {
                    override val key: String
                        get() = key
                }
            }
            coordinator.cancelTimer(key)
            assertFalse(timerLatch.await(TIMER_DELAY, TimeUnit.MILLISECONDS))
        }
        assertEquals(0, deliveredTimerEvents)
    }

    @Test
    fun postHandledErrorEvent() {
        var stopLatch = CountDownLatch(1)
        var startLatch = CountDownLatch(1)
        val expectedException = Exception("expected exception")
        var exceptionCount = 0
        createCoordinator { event: LifecycleEvent, coordinator: LifecycleCoordinator ->
            when (event) {
                is StartEvent -> {
                    startLatch.countDown()
                }
                is ThrowException -> {
                    throw expectedException
                }
                is ErrorEvent -> {
                    assertEquals(expectedException, event.cause)
                    event.isHandled = true
                    exceptionCount++
                    coordinator.postEvent(object : PostEvent {})
                }
                is PostEvent -> {
                    coordinator.stop()
                }
                is StopEvent -> {
                    stopLatch.countDown()
                }
            }
        }.use { coordinator ->
            for (i in 0..NUM_LOOPS) {
                startLatch = CountDownLatch(1)
                stopLatch = CountDownLatch(1)
                coordinator.start()
                assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
                coordinator.postEvent(object : ThrowException {})
                assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            }
        }
        assertEquals(NUM_LOOPS + 1, exceptionCount)
    }

    @Test
    fun postHandledButRethrowErrorEvent() {
        var stopLatch = CountDownLatch(1)
        var startLatch = CountDownLatch(1)
        val expectedException = Exception("expected exception")
        val unexpectedException = Exception("unexpected exception")
        var exceptionCount = 0
        var unexpectedExceptionCount = 0
        createCoordinator { event: LifecycleEvent, _: LifecycleCoordinator ->
            when (event) {
                is StartEvent -> {
                    startLatch.countDown()
                }
                is ThrowException -> {
                    throw expectedException
                }
                is ErrorEvent -> {
                    when (event.cause) {
                        expectedException -> {
                            event.isHandled = true
                            exceptionCount++
                            throw unexpectedException
                        }
                        else -> {
                            unexpectedExceptionCount++
                        }
                    }
                }
                is StopEvent -> {
                    stopLatch.countDown()
                }
            }
        }.use { coordinator ->
            for (i in 0..NUM_LOOPS) {
                startLatch = CountDownLatch(1)
                stopLatch = CountDownLatch(1)
                coordinator.start()
                assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
                coordinator.postEvent(object : ThrowException {})
                assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            }
        }
        assertEquals(NUM_LOOPS + 1, exceptionCount)
        assertEquals(0, unexpectedExceptionCount)
    }


    @Test
    fun postUnhandledErrorEvent() {
        var stopLatch = CountDownLatch(1)
        var startLatch = CountDownLatch(1)
        val expectedException = Exception("expected exception")
        var exceptionCount = 0
        var unexpectedExceptionCount = 0
        createCoordinator { event: LifecycleEvent, _: LifecycleCoordinator ->
            when (event) {
                is StartEvent -> {
                    startLatch.countDown()
                }
                is ThrowException -> {
                    throw expectedException
                }
                is ErrorEvent -> {
                    when (event.cause) {
                        expectedException -> {
                            exceptionCount++
                        }
                        else -> {
                            unexpectedExceptionCount++
                        }
                    }
                }
                is StopEvent -> {
                    stopLatch.countDown()
                }
            }
        }.use { coordinator ->
            for (i in 0..NUM_LOOPS) {
                startLatch = CountDownLatch(1)
                stopLatch = CountDownLatch(1)
                coordinator.start()
                assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
                coordinator.postEvent(object : ThrowException {})
                assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            }
        }
        assertEquals(NUM_LOOPS + 1, exceptionCount)
        assertEquals(0, unexpectedExceptionCount)
    }

    @Test
    fun setTimer() {
        val startLatch = CountDownLatch(1)
        val key = "wait_for_me"
        val timerLatch = CountDownLatch(1)
        val stopLatch = CountDownLatch(1)
        var deliveredKey = "the_wrong_key"
        createCoordinator { event: LifecycleEvent, _: LifecycleCoordinator ->
            when (event) {
                is StartEvent -> startLatch.countDown()
                is TimerEvent -> {
                    deliveredKey = event.key
                    timerLatch.countDown()
                }
                is StopEvent -> stopLatch.countDown()
            }
        }.use { coordinator ->
            coordinator.start()
            assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            coordinator.setTimer(key, TIMER_DELAY) {
                object : TimerEvent {
                    override val key: String
                        get() = key
                }
            }
            assertTrue(timerLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
        }
        assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
        assertEquals(key, deliveredKey)
    }

    @Test
    fun startAndStopLoop() {
        val startLatch = CountDownLatch(NUM_LOOPS)
        val stopLatch = CountDownLatch(NUM_LOOPS)
        var startedRunningLatch = CountDownLatch(1)
        var stoppedRunningLatch = CountDownLatch(1)
        createCoordinator { event: LifecycleEvent, _: LifecycleCoordinator ->
            when (event) {
                is StartEvent -> {
                    startLatch.countDown()
                    startedRunningLatch.countDown()
                }
                is StopEvent -> {
                    stopLatch.countDown()
                    stoppedRunningLatch.countDown()
                }
            }
        }.use { coordinator ->
            for (i in 0..NUM_LOOPS) {
                startedRunningLatch = CountDownLatch(1)
                stoppedRunningLatch = CountDownLatch(1)
                assertFalse(coordinator.isRunning)
                coordinator.start()
                assertTrue(startedRunningLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
                assertTrue(coordinator.isRunning)
                coordinator.stop()
                assertTrue(stoppedRunningLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            }
            assertTrue(startLatch.await(NUM_LOOPS * TIMEOUT, TimeUnit.MILLISECONDS))
            assertTrue(stopLatch.await(NUM_LOOPS * TIMEOUT, TimeUnit.MILLISECONDS))
        }
    }

    @Test
    fun `calling start and stop multiple times in a row only delivers a single start and stop event`() {
        val startLatch = CountDownLatch(1)
        val stopLatch = CountDownLatch(1)
        var startCount = 0
        var stopCount = 0
        createCoordinator { event, _ ->
            when (event) {
                is StartEvent -> {
                    startCount++
                    startLatch.countDown()
                }
                is StopEvent -> {
                    stopCount++
                    stopLatch.countDown()
                }
            }
        }.use {
            it.stop()
            it.start()
            it.start()
            it.stop()
            it.stop()
            assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            // For sanity
            assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertEquals(1, startCount)
            assertEquals(1, stopCount)
        }
    }

    @Test
    fun `calling stop inside start event causes correct termination of coordinator`() {
        val startLatch = CountDownLatch(1)
        val stopLatch = CountDownLatch(1)
        createCoordinator { event, coordinator ->
            when (event) {
                is StartEvent -> {
                    startLatch.countDown()
                    coordinator.stop()
                }
                is StopEvent -> {
                    stopLatch.countDown()
                }
            }
        }.use {
            it.start()
            assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            // For sanity
            assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertFalse(it.isRunning)
        }
    }

    @Test
    fun `given a chain of events, when each is posted, they are processed in the correct order`() {
        val event1 = object : LifecycleEvent {}
        val event2 = object : LifecycleEvent {}
        val event3 = object : LifecycleEvent {}
        val stopLatch = CountDownLatch(1)
        var previousEvent: LifecycleEvent? = null
        createCoordinator { event, coordinator ->
            when (event) {
                is StartEvent -> {
                    assertEquals(null, previousEvent)
                    previousEvent = event
                    coordinator.postEvent(event1)
                }
                event1 -> {
                    assertTrue(previousEvent is StartEvent)
                    previousEvent = event
                    coordinator.postEvent(event2)
                }
                event2 -> {
                    assertEquals(event1, previousEvent)
                    previousEvent = event
                    coordinator.postEvent(event3)
                }
                event3 -> {
                    assertEquals(event2, previousEvent)
                    previousEvent = event
                    coordinator.stop()
                }
                is StopEvent -> {
                    assertEquals(event3, previousEvent)
                    stopLatch.countDown()
                }
            }
        }.use {
            it.start()
            assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
        }
    }

    @Test
    fun `calling stop inside the stop event does not redeliver stop`() {
        val startLatch = CountDownLatch(1)
        val stopLatch = CountDownLatch(1)
        var startCount = 0
        var stopCount = 0
        createCoordinator { event, coordinator ->
            when (event) {
                is StartEvent -> {
                    startCount++
                    startLatch.countDown()
                }
                is StopEvent -> {
                    // This could potentially re-trigger cleanup, which would redeliver all events so far.
                    coordinator.stop()
                    stopCount++
                    stopLatch.countDown()
                }
            }
        }.use {
            it.start()
            it.stop()
            assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            // For sanity
            assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertEquals(1, startCount)
            assertEquals(1, stopCount)
        }
    }

    @Test
    fun `can handle exceptions being thrown when processing the start or stop event`() {
        val startLatch = CountDownLatch(1)
        val stopLatch = CountDownLatch(1)
        val exceptionLatch = CountDownLatch(2)
        val exception = Exception("A bad thing happened")
        var startCount = 0
        var stopCount = 0
        var exceptionCount = 0
        var unexpectedExceptionCount = 0
        createCoordinator { event, _ ->
            when (event) {
                is StartEvent -> {
                    startCount++
                    startLatch.countDown()
                    throw exception
                }
                is ErrorEvent -> {
                    event.isHandled = true
                    if (event.cause == exception) {
                        exceptionCount++
                    } else {
                        unexpectedExceptionCount++
                    }
                    exceptionLatch.countDown()
                }
                is StopEvent -> {
                    stopCount++
                    stopLatch.countDown()
                    throw exception
                }
            }
        }.use {
            it.start()
            it.stop()
            assertTrue(exceptionLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertEquals(1, startCount)
            assertEquals(1, stopCount)
        }
        assertEquals(2, exceptionCount)
        assertEquals(0, unexpectedExceptionCount)
    }

    @Test
    fun `events posted while the coordinator is stopped are not delivered`() {
        val whileStopped = object : LifecycleEvent {}
        val whileStarted = object : LifecycleEvent {}
        val startLatch = CountDownLatch(1)
        val stopLatch = CountDownLatch(1)
        var startCount = 0
        var stopCount = 0
        var whileStartedProcessed = 0
        var whileStoppedProcessed = 0
        createCoordinator { event, _ ->
            when (event) {
                is StartEvent -> {
                    startCount++
                    startLatch.countDown()
                }
                is StopEvent -> {
                    stopCount++
                    stopLatch.countDown()
                }
                else -> {
                    if (event == whileStarted) {
                        whileStartedProcessed++
                    } else {
                        whileStoppedProcessed++
                    }
                }
            }
        }.use {
            it.postEvent(whileStopped)
            it.start()
            assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            it.postEvent(whileStarted)
            it.postEvent(whileStarted)
            it.postEvent(whileStarted)
            it.stop()
            it.postEvent(whileStopped)
            assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            it.postEvent(whileStopped)
        }
        assertEquals(3, whileStartedProcessed)
        assertEquals(0, whileStoppedProcessed)
    }

    @Test
    fun `an unhandled error in the start handler causes a stop event to be delivered`() {
        val startLatch = CountDownLatch(1)
        val stopLatch = CountDownLatch(1)
        val exceptionLatch = CountDownLatch(2)
        val exception = Exception("A bad thing happened")
        var startCount = 0
        var stopCount = 0
        var expectedExceptionCount = 0
        var unexpectedExceptionCount = 0
        createCoordinator { event, _ ->
            when (event) {
                is StartEvent -> {
                    startCount++
                    startLatch.countDown()
                    throw exception
                }
                is ErrorEvent -> {
                    if (event.cause == exception) {
                        expectedExceptionCount++
                    } else {
                        unexpectedExceptionCount++
                    }
                    exceptionLatch.countDown()
                }
                is StopEvent -> {
                    stopCount++
                    stopLatch.countDown()
                    throw exception
                }
            }
        }.use {
            it.start()
            assertTrue(exceptionLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertEquals(1, startCount)
            assertEquals(1, stopCount)
        }
        assertEquals(2, expectedExceptionCount)
        assertEquals(0, unexpectedExceptionCount)
    }

    @Test
    fun `status of a coordinator can be updated`() {
        val startLatch = CountDownLatch(1)
        val stopLatch = CountDownLatch(1)
        val flushingLatch = CountDownLatch(1)
        val flushingEvent = object : LifecycleEvent {}
        val registry = mock<LifecycleRegistryCoordinatorAccess>()
        createCoordinator(registry) { event, _ ->
            when (event) {
                is StartEvent -> {
                    startLatch.countDown()
                }
                is StopEvent -> {
                    stopLatch.countDown()
                }
                flushingEvent -> {
                    flushingLatch.countDown()
                }
            }
        }.use {
            it.start()
            assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertEquals(LifecycleStatus.DOWN, it.status)

            // Request a status update, then post an event to flush through the status update. Should be guaranteed to
            // have processed the status update by the time flushing event is seen in the processor.
            it.updateStatus(LifecycleStatus.UP, REASON)
            it.postEvent(flushingEvent)
            assertTrue(flushingLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertEquals(LifecycleStatus.UP, it.status)
            verify(registry).updateStatus(
                LifecycleCoordinatorName.forComponent<LifecycleCoordinatorImplTest>(),
                LifecycleStatus.UP,
                REASON
            )

            // Stopping should set the coordinator to down
            it.stop()
            assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertEquals(LifecycleStatus.DOWN, it.status)
            verify(registry).updateStatus(
                LifecycleCoordinatorName.forComponent<LifecycleCoordinatorImplTest>(),
                LifecycleStatus.DOWN,
                STOPPED_REASON
            )
        }
    }

    @Test
    fun `coordinator status should not be updated before the coordinator has been started`() {
        val startLatch = CountDownLatch(1)
        val stopLatch = CountDownLatch(1)
        createCoordinator { event, _ ->
            when (event) {
                is StartEvent -> {
                    startLatch.countDown()
                }
                is StopEvent -> {
                    stopLatch.countDown()
                }
            }
        }.use {
            it.updateStatus(LifecycleStatus.UP)
            it.start()
            assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertEquals(LifecycleStatus.DOWN, it.status)
            it.stop()
            assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
        }
    }

    @Test
    fun `an active status change event is delivered when dependent coordinator statuses change`() {
        val startLatch = CountDownLatch(1)
        val stopLatch = CountDownLatch(1)
        val dependent1 = createCoordinator { _, _ -> }
        val dependent2 = createCoordinator { _, _ -> }
        var registration: RegistrationHandle? = null
        var status = LifecycleStatus.DOWN
        var regLatch = CountDownLatch(1)
        createCoordinator { event, _ ->
            when (event) {
                is StartEvent -> {
                    startLatch.countDown()
                }
                is StopEvent -> {
                    stopLatch.countDown()
                }
                is RegistrationStatusChangeEvent -> {
                    registration = event.registration
                    status = event.status
                    regLatch.countDown()
                }
            }
        }.use {
            it.start()
            assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            val handle = it.followStatusChanges(setOf(dependent1, dependent2))

            // Set both dependents up
            dependent1.start()
            dependent2.start()
            dependent1.updateStatus(LifecycleStatus.UP)
            dependent2.updateStatus(LifecycleStatus.UP)
            assertTrue(regLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertEquals(handle, registration)
            assertEquals(LifecycleStatus.UP, status)

            // Take one down and verify that down is delivered
            regLatch = CountDownLatch(1)
            dependent1.updateStatus(LifecycleStatus.DOWN)
            assertTrue(regLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertEquals(handle, registration)
            assertEquals(LifecycleStatus.DOWN, status)

            // Put the dependent back up again
            regLatch = CountDownLatch(1)
            dependent1.updateStatus(LifecycleStatus.UP)
            assertTrue(regLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertEquals(handle, registration)
            assertEquals(LifecycleStatus.UP, status)

            // Stop a dependent
            regLatch = CountDownLatch(1)
            dependent1.stop()
            assertTrue(regLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertEquals(handle, registration)
            assertEquals(LifecycleStatus.DOWN, status)

            handle.close()
            it.stop()
            assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
        }
    }

    @Test
    fun `closing a registration prevents active status updates from being delivered`() {
        val startLatch = CountDownLatch(1)
        val stopLatch = CountDownLatch(1)
        val dependentLatch = CountDownLatch(1)
        val dependent1 = createCoordinator { event, _ ->
            if (event is StopEvent) {
                dependentLatch.countDown()
            }
        }
        val dependent2 = createCoordinator { _, _ -> }
        var registration: RegistrationHandle? = null
        var status = LifecycleStatus.DOWN
        val regLatch = CountDownLatch(1)
        var totalStatusChanges = 0
        createCoordinator { event, _ ->
            when (event) {
                is StartEvent -> {
                    startLatch.countDown()
                }
                is StopEvent -> {
                    stopLatch.countDown()
                }
                is RegistrationStatusChangeEvent -> {
                    registration = event.registration
                    status = event.status
                    totalStatusChanges++
                    regLatch.countDown()
                }
            }
        }.use {
            it.start()
            assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            val handle = it.followStatusChanges(setOf(dependent1, dependent2))

            dependent1.start()
            dependent2.start()
            dependent1.updateStatus(LifecycleStatus.UP)
            dependent2.updateStatus(LifecycleStatus.UP)
            assertTrue(regLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertEquals(handle, registration)
            assertEquals(LifecycleStatus.UP, status)

            // Close the registration and bring down a dependent coordinator
            handle.close()
            dependent1.updateStatus(LifecycleStatus.DOWN)
            // Bring down the dependent and wait - this ensures that the status update has definitely been delivered.
            dependent1.stop()
            assertTrue(dependentLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertEquals(1, totalStatusChanges) // Just the up change
            assertEquals(LifecycleStatus.UP, status) // Down wasn't delivered.
        }
    }

    @Test
    fun `status updates are delivered across overlapping follow sets of coordinators`() {
        val startLatch = CountDownLatch(1)
        val stopLatch = CountDownLatch(1)
        var regLatch = CountDownLatch(2)
        val dependent1 = createCoordinator { _, _ -> }
        val dependent2 = createCoordinator { _, _ -> }
        val dependent3 = createCoordinator { _, _ -> }
        var totalStatusChanges = 0
        val handleSet = mutableSetOf<RegistrationHandle>()
        createCoordinator { event, _ ->
            when (event) {
                is StartEvent -> {
                    startLatch.countDown()
                }
                is StopEvent -> {
                    stopLatch.countDown()
                }
                is RegistrationStatusChangeEvent -> {
                    handleSet.add(event.registration)
                    totalStatusChanges++
                    regLatch.countDown()
                }
            }
        }.use {
            it.start()
            dependent1.start()
            dependent2.start()
            dependent3.start()
            assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))

            // Create overlapping registrations and show that both get delivered.
            val handle1 = it.followStatusChanges(setOf(dependent1, dependent2))
            val handle2 = it.followStatusChanges(setOf(dependent2, dependent3))
            dependent1.updateStatus(LifecycleStatus.UP)
            dependent2.updateStatus(LifecycleStatus.UP)
            dependent3.updateStatus(LifecycleStatus.UP)
            assertTrue(regLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertEquals(2, totalStatusChanges)
            assertEquals(setOf(handle1, handle2), handleSet)

            handleSet.clear()
            regLatch = CountDownLatch(2)
            dependent2.updateStatus(LifecycleStatus.DOWN)
            assertTrue(regLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertEquals(4, totalStatusChanges)
            assertEquals(setOf(handle1, handle2), handleSet)
            handle1.close()
            handle2.close()
            it.stop()
            assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
        }
    }

    @Test
    fun `an up event is delivered when the coordinator starts if all dependent coordinators are up`() {
        val startLatch = CountDownLatch(1)
        val stopLatch = CountDownLatch(1)
        val dependentsUpLatch = CountDownLatch(2)
        val flushingEvent = object : LifecycleEvent {}
        val dependent1 = createCoordinator { event, _ ->
            when (event) {
                flushingEvent -> {
                    dependentsUpLatch.countDown()
                }
            }
        }
        val dependent2 = createCoordinator { event, _ ->
            when (event) {
                flushingEvent -> {
                    dependentsUpLatch.countDown()
                }
            }
        }
        var registration: RegistrationHandle? = null
        var status = LifecycleStatus.DOWN
        val regLatch = CountDownLatch(1)
        createCoordinator { event, _ ->
            when (event) {
                is StartEvent -> {
                    startLatch.countDown()
                }
                is StopEvent -> {
                    stopLatch.countDown()
                }
                is RegistrationStatusChangeEvent -> {
                    registration = event.registration
                    status = event.status
                    regLatch.countDown()
                }
            }
        }.use {
            dependent1.start()
            dependent2.start()
            val handle = it.followStatusChanges(setOf(dependent1, dependent2))
            dependent1.updateStatus(LifecycleStatus.UP)
            dependent2.updateStatus(LifecycleStatus.UP)
            dependent1.postEvent(flushingEvent)
            dependent2.postEvent(flushingEvent)
            assertTrue(dependentsUpLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            it.start()
            assertTrue(regLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertEquals(handle, registration)
            assertEquals(LifecycleStatus.UP, status)
            handle.close()
            it.stop()
            assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
        }
    }

    @Test
    @Suppress("TooGenericExceptionThrown")
    fun `when a coordinator stops with an error the status is set to error`() {
        var startLatch = CountDownLatch(1)
        val stopLatch = CountDownLatch(1)
        createCoordinator { event, _ ->
            when (event) {
                is StartEvent -> {
                    startLatch.countDown()
                }
                is ThrowException -> {
                    throw Exception("Something went wrong")
                }
                is StopEvent -> {
                    stopLatch.countDown()
                }
            }
        }.use {
            it.start()
            assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            it.postEvent(object : ThrowException {})
            assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertEquals(LifecycleStatus.ERROR, it.status)

            // Restart and prove that the status is set to DOWN.
            startLatch = CountDownLatch(1)
            it.start()
            assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertEquals(LifecycleStatus.DOWN, it.status)
        }
    }

    @Test
    fun `can register on coordinators using the registry`() {
        val startLatch = CountDownLatch(1)
        val stopLatch = CountDownLatch(1)
        val registry = mock<LifecycleRegistryCoordinatorAccess>()
        val coordinatorA = createCoordinator { _, _ -> }
        val coordinatorB = createCoordinator { _, _ -> }
        val nameA = LifecycleCoordinatorName("Alice")
        val nameB = LifecycleCoordinatorName("Bob")
        doReturn(coordinatorA).`when`(registry).getCoordinator(nameA)
        doReturn(coordinatorB).`when`(registry).getCoordinator(nameB)
        val registrationLatch = CountDownLatch(1)
        var registration: RegistrationHandle? = null
        var status = LifecycleStatus.DOWN
        createCoordinator(registry) { event, _ ->
            when (event) {
                is StartEvent -> {
                    startLatch.countDown()
                }
                is StopEvent -> {
                    stopLatch.countDown()
                }
                is RegistrationStatusChangeEvent -> {
                    registration = event.registration
                    status = event.status
                    registrationLatch.countDown()
                }
            }
        }.use {
            it.start()
            assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            val handle = it.followStatusChangesByName(setOf(nameA, nameB))
            // Set both dependent coordinators to UP and verify the event goes through correctly.
            coordinatorA.start()
            coordinatorB.start()
            coordinatorA.updateStatus(LifecycleStatus.UP)
            coordinatorB.updateStatus(LifecycleStatus.UP)
            assertTrue(registrationLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertEquals(handle, registration)
            assertEquals(LifecycleStatus.UP, status)
            handle.close()
            it.stop()
            assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
        }
    }

    @Test
    fun `exception is thrown if an attempt is made to register on a coordinator that does not exist`() {
        val startLatch = CountDownLatch(1)
        val registry = mock<LifecycleRegistryCoordinatorAccess>()
        val coordinatorA = createCoordinator { _, _ -> }
        val coordinatorB = createCoordinator { _, _ -> }
        val nameA = LifecycleCoordinatorName("Alice")
        val nameB = LifecycleCoordinatorName("Bob")
        val nameC = LifecycleCoordinatorName("Charlie")
        doReturn(coordinatorA).`when`(registry).getCoordinator(nameA)
        doReturn(coordinatorB).`when`(registry).getCoordinator(nameB)
        // Note we need to use doAnswer here as doThrows requires the function to declare it throws the exception type
        doAnswer { throw LifecycleException("Charlie doesn't exist") }.`when`(registry).getCoordinator(nameC)
        createCoordinator(registry) { event, _ ->
            when (event) {
                is StartEvent -> {
                    startLatch.countDown()
                }
            }
        }.use {
            it.start()
            assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            assertThrows<LifecycleException> {
                it.followStatusChangesByName(setOf(nameA, nameB, nameC))
            }
        }
    }

    @Test
    fun `exception is thrown if attempt is made to register on yourself`() {
        val coordinator = createCoordinator { _, _ -> }
        assertThrows<LifecycleException> {
            coordinator.followStatusChanges(setOf(coordinator))
        }
    }

    @Test
    fun `custom events are sent to registered coordinators`() {
        val startLatch = CountDownLatch(1)
        class CustomEventData(val value: String)
        val receivedCustomEvents = mutableListOf<CustomEvent>()

        val trackedCoordinator = createCoordinator { event, _ ->
            when(event) {
                is StartEvent -> {
                    startLatch.countDown()
                }
            }
        }
        val trackingCoordinator = createCoordinator { event, _ ->
            when(event) {
                is CustomEvent -> {
                    receivedCustomEvents.add(event)
                }
            }
        }

        trackingCoordinator.followStatusChanges(setOf(trackedCoordinator))
        trackedCoordinator.start()
        trackingCoordinator.start()
        startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)

        val eventData = "hello world"
        trackedCoordinator.postCustomEventToFollowers(CustomEventData(eventData))
        eventually {
            val customEvents = receivedCustomEvents.map { it.payload }
                .filterIsInstance<CustomEventData>()
                .filter { it.value == eventData }
            assertThat(customEvents).hasSize(1)
        }
    }

    @Test
    fun `closing a coordinator prevents subsequent calls to restart or post events`() {
        val startLatch = CountDownLatch(1)
        val stopLatch = CountDownLatch(1)
        val registry = mock<LifecycleRegistryCoordinatorAccess>()
        val registered = createCoordinator { _, _ -> }
        val coordinator = createCoordinator(registry) { event, _ ->
            when (event) {
                is StartEvent -> {
                    startLatch.countDown()
                }
                is StopEvent -> {
                    stopLatch.countDown()
                }
            }
        }
        coordinator.use {
            it.start()
            assertTrue(startLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
        }
        assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
        verify(registry).removeCoordinator(coordinator.name)
        assertThrows<LifecycleException> {
            coordinator.start()
        }
        assertThrows<LifecycleException> {
            coordinator.postEvent(object : LifecycleEvent {})
        }
        assertThrows<LifecycleException> {
            coordinator.followStatusChanges(setOf(registered))
        }
    }

    @Test
    fun `can still close coordinator even if there are registrations`() {
        val latch = CountDownLatch(2)
        val flushingEvent = object : LifecycleEvent {}
        val coordinator1 = createCoordinator { event, _ ->
            when (event) {
                flushingEvent -> {
                    latch.countDown()
                }
            }
        }
        val coordinator2 = createCoordinator { event, _ ->
            when (event) {
                flushingEvent -> {
                    latch.countDown()
                }
            }
        }
        coordinator1.start()
        coordinator2.start()
        coordinator1.postEvent(flushingEvent)
        coordinator2.postEvent(flushingEvent)
        assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS))
        coordinator1.close()
        coordinator2.close()

        assertThat(coordinator1.isClosed)
        assertThat(coordinator2.isClosed)
    }

    @Test
    fun `not closing a coordinator registration eventually leads to a down state`() {
        val trackedLatch = CountDownLatch(1)
        val factory = LifecycleCoordinatorFactoryImpl(mock())
        val coordinator = factory.createCoordinator(LifecycleCoordinatorName("a", "b")) { _, _ -> }
        coordinator.start()

        val tracked = factory.createCoordinator(LifecycleCoordinatorName("a 1", "b 1")) { event, _ ->
            if (event is StopEvent) {
                trackedLatch.countDown()
            }
        }
        tracked.start()
        coordinator.followStatusChanges(setOf(tracked))

        tracked.close()
        trackedLatch.await(1, TimeUnit.SECONDS)
        assertThat(tracked.status).isEqualTo(LifecycleStatus.DOWN)
        assertThat(coordinator.status).isEqualTo(LifecycleStatus.DOWN)
    }

    private fun createCoordinator(
        registry: LifecycleRegistryCoordinatorAccess = mock(),
        processor: LifecycleEventHandler
    ): LifecycleCoordinator {
        return LifecycleCoordinatorImpl(
            LifecycleCoordinatorName.forComponent<LifecycleCoordinatorImplTest>(),
            BATCH_SIZE,
            registry,
            processor
        )
    }
}
