package net.corda.lifecycle

import net.corda.v5.base.util.contextLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.slf4j.Logger
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
internal class SimpleLifecycleCoordinatorTest {

    companion object {

        private const val BATCH_SIZE: Int = 128

        private const val TIMEOUT: Long = 500L

        private const val TIMER_DELAY = 100L

        private const val NUM_LOOPS = 5

        val logger: Logger = contextLogger()
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
        SimpleLifecycleCoordinator(BATCH_SIZE) { event: LifecycleEvent, _: LifecycleCoordinator ->
            logger.debug("processEvent $event")
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
        SimpleLifecycleCoordinator(BATCH_SIZE) { event: LifecycleEvent, _: LifecycleCoordinator ->
            logger.debug("processEvent $event")
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
        SimpleLifecycleCoordinator(BATCH_SIZE) { event: LifecycleEvent, _: LifecycleCoordinator ->
            logger.debug("processEvent $event")
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
        SimpleLifecycleCoordinator(BATCH_SIZE) { event: LifecycleEvent, coordinator: LifecycleCoordinator ->
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
        SimpleLifecycleCoordinator(BATCH_SIZE) { event: LifecycleEvent, _: LifecycleCoordinator ->
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
        SimpleLifecycleCoordinator(BATCH_SIZE) { event: LifecycleEvent, _: LifecycleCoordinator ->
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
            for(i in 0 .. NUM_LOOPS) {
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
        SimpleLifecycleCoordinator(BATCH_SIZE) { event: LifecycleEvent, _: LifecycleCoordinator ->
            logger.debug("processEvent $event")
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
        SimpleLifecycleCoordinator(BATCH_SIZE) { event: LifecycleEvent, _: LifecycleCoordinator ->
            logger.debug("processEvent $event")
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
        SimpleLifecycleCoordinator(BATCH_SIZE) { event, _ ->
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
        SimpleLifecycleCoordinator(BATCH_SIZE) { event, coordinator ->
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
        var previousEvent : LifecycleEvent? = null
        SimpleLifecycleCoordinator(BATCH_SIZE) { event, coordinator ->
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
        SimpleLifecycleCoordinator(BATCH_SIZE) { event, coordinator ->
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
        SimpleLifecycleCoordinator(BATCH_SIZE) { event, _ ->
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
        SimpleLifecycleCoordinator(BATCH_SIZE) { event, _ ->
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
        SimpleLifecycleCoordinator(BATCH_SIZE) { event, _ ->
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
}