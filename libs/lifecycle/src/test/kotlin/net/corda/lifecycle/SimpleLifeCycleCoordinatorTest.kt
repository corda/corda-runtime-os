package net.corda.lifecycle

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

internal class SimpleLifeCycleCoordinatorTest {

    companion object {

        const val BATCH_SIZE: Int = 128

        const val TIMEOUT: Long = 1000L

        val logger: Logger = LoggerFactory.getLogger(SimpleLifeCycleCoordinatorTest::class.java)
    }

    interface PostEvent : LifeCycleEvent

    @Test
    fun burstEvents() {
        val n = BATCH_SIZE * 2
        val startLatch = CountDownLatch(1)
        val countDownLatch = CountDownLatch(n)  // Used to test all posted events are processed when coordinator stopped.
        val stopLatch = CountDownLatch(1)
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT * n) { event: LifeCycleEvent, _: LifeCycleCoordinator ->
            logger.debug("processEvent $event")
            when (event) {
                is StartEvent -> {
                    startLatch.countDown()
                }
                is PostEvent -> {
                    countDownLatch.countDown()
                }
                is StopEvent -> {
                    stopLatch.countDown()
                }
                else -> {
                    fail("$event unexpected!")
                }
            }
        }.use { coordinator ->
            coordinator.start()
            assertTrue(startLatch.await(coordinator.timeout, TimeUnit.MILLISECONDS))
            for (i in 0 until n) {
                coordinator.postEvent(object : PostEvent {})
            }
            coordinator.stop()
            assertTrue(stopLatch.await(coordinator.timeout * n, TimeUnit.MILLISECONDS))
            assertTrue(countDownLatch.await(coordinator.timeout * n, TimeUnit.MILLISECONDS))
        }
    }

    @Test
    fun burstTimers() {
        val n = BATCH_SIZE / 2
        val startLatch = CountDownLatch(1)
        val countDownLatch = CountDownLatch(n)  // Used to test all posted events are processed when coordinator stopped.
        val stopLatch = CountDownLatch(1)
        SimpleLifeCycleCoordinator(BATCH_SIZE,
            TIMEOUT * n) { event: LifeCycleEvent, _: LifeCycleCoordinator ->
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
                    fail("$event unexpected!")
                }
            }
        }.use { coordinator ->
            coordinator.start()
            assertTrue(startLatch.await(coordinator.timeout, TimeUnit.MILLISECONDS))
            for (i in 0 until n) {
                val onTime = object : TimerEvent {
                    override val key: String
                        get() = i.toString()
                }
                val delay = Random.nextLong(0, TIMEOUT / 2)
                coordinator.setTimer(onTime.key, delay) { onTime }
            }
            Thread {
                while (countDownLatch.count >= coordinator.batchSize / 2) {
                    Thread.sleep(Random.nextLong(0, TIMEOUT / 20))
                }
                coordinator.stop()
            }.start()
            assertTrue(stopLatch.await(coordinator.timeout * n, TimeUnit.MILLISECONDS))
        }
        assertTrue(n > countDownLatch.count)
    }

    @Test
    fun cancelTimer() {
        val startLatch = CountDownLatch(1)
        val key = "kill_me_softly"
        val timerLatch = CountDownLatch(1)
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { event: LifeCycleEvent, _: LifeCycleCoordinator ->
            logger.debug("processEvent $event")
            when (event) {
                is StartEvent -> startLatch.countDown()
                is TimerEvent -> {
                    fail("${event}.${event.key} cancelled but executed! ")
                }
            }
        }.use { coordinator ->
            coordinator.start()
            assertTrue(startLatch.await(TIMEOUT * 2, TimeUnit.MILLISECONDS))
            coordinator.setTimer(key, TIMEOUT / 2) {
                object : TimerEvent {
                    override val key: String
                        get() = key
                }
            }
            coordinator.cancelTimer(key)
            assertFalse(timerLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
        }
    }

    @Test
    fun postErrorEvent() {
        val expected = Exception("test exception")
        val errorLatch = CountDownLatch(1)
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { event: LifeCycleEvent, _: LifeCycleCoordinator ->
            when (event) {
                is PostEvent -> {
                    throw expected
                }
                is ErrorEvent -> {
                    assertEquals(expected, event.cause)
                    errorLatch.countDown()
                }
            }
        }.use { coordinator ->
            coordinator.start()
            coordinator.postEvent(object : PostEvent {})
            assertTrue(errorLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
        }
    }

    @Disabled("Debugger problems? To check...")
    @Test
    fun postHandledErrorEvent() {
        val expected = Exception("test exception")
        val toHandle = Exception("test to handle")
        val errorLatch = CountDownLatch(1)
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { event: LifeCycleEvent, _: LifeCycleCoordinator ->
            when (event) {
                is PostEvent -> {
                    throw expected
                }
                is ErrorEvent -> {
                    when (event.cause) {
                        expected -> {
                            event.isHandled = true
                            throw toHandle
                        }
                        toHandle -> {
                            event.isHandled = true
                            errorLatch.countDown()
                        }
                        else -> {
                            fail("Error event unexpected!")
                        }
                    }
                }
                is StopEvent -> {
                    fail("Stop event unexpected!")
                }
            }
        }.use { coordinator ->
            coordinator.start()
            coordinator.postEvent(object : PostEvent {})
            assertTrue(errorLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
        }
    }

    @Test
    fun postUnhandledErrorEvent() {
        val expected = Exception("test exception")
        val unexpected = Exception("test unhandled")
        val stopLatch = CountDownLatch(1)
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { event: LifeCycleEvent, _: LifeCycleCoordinator ->
            when (event) {
                is PostEvent -> {
                    throw expected
                }
                is ErrorEvent -> {
                    assertEquals(expected, event.cause)
                    throw unexpected
                }
                is StopEvent -> {
                    stopLatch.countDown()
                }
            }
        }.use { coordinator ->
            coordinator.start()
            coordinator.postEvent(object : PostEvent {})
            assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
        }
    }

    @Test
    fun postEvent() {
        val postLatch = CountDownLatch(1)
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { event: LifeCycleEvent, _: LifeCycleCoordinator ->
            when (event) {
                is PostEvent -> postLatch.countDown()
            }
        }.use { coordinator ->
            coordinator.start()
            coordinator.postEvent(object : PostEvent {})
            assertTrue(postLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
        }
    }

    @Test
    fun setTimer() {
        val startLatch = CountDownLatch(1)
        val key = "wait_for_me"
        val timerLatch = CountDownLatch(1)
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { event: LifeCycleEvent, _: LifeCycleCoordinator ->
            logger.debug("processEvent $event")
            when (event) {
                is StartEvent -> startLatch.countDown()
                is TimerEvent -> {
                    timerLatch.countDown()
                    assertEquals(key, event.key)
                }
            }
        }.use { coordinator ->
            coordinator.start()
            assertTrue(startLatch.await(TIMEOUT * 2, TimeUnit.MILLISECONDS))
            coordinator.setTimer(key, TIMEOUT / 2) {
                object : TimerEvent {
                    override val key: String
                        get() = key
                }
            }
            assertTrue(timerLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
        }
    }

    @Test
    fun start() {
        val startLatch = CountDownLatch(1)
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { event: LifeCycleEvent, _: LifeCycleCoordinator ->
            logger.debug("processEvent $event")
            when (event) {
                is StartEvent -> startLatch.countDown()
            }
        }.use { coordinator ->
            assertFalse(coordinator.isRunning)
            coordinator.start()
            assertTrue(startLatch.await(TIMEOUT * 2, TimeUnit.MILLISECONDS))
            assertTrue(coordinator.isRunning)
        }
    }

    @Test
    fun stop() {
        var startLatch = CountDownLatch(1)
        var stopLatch = CountDownLatch(1)
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { event: LifeCycleEvent, _: LifeCycleCoordinator ->
            logger.debug("processEvent $event")
            when (event) {
                is StartEvent -> startLatch.countDown()
                is StopEvent -> stopLatch.countDown()
            }
        }.use { coordinator ->
            for (i in 0..3) {
                coordinator.start()
                assertTrue(startLatch.await(TIMEOUT * 2, TimeUnit.MILLISECONDS))
                assertTrue(coordinator.isRunning)
                coordinator.stop()
                assertTrue(stopLatch.await(TIMEOUT * 2, TimeUnit.MILLISECONDS))
                assertFalse(coordinator.isRunning)
                startLatch = CountDownLatch(1)
                stopLatch = CountDownLatch(1)
            }
            // start again
        }
    }

    // create events. stop, check all events are processed.

    @Test
    fun getBatchSize() {
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { _: LifeCycleEvent, _: LifeCycleCoordinator -> }
            .use { coordinator ->
                assertEquals(BATCH_SIZE, coordinator.batchSize)
            }
    }

    @Test
    fun getTimeout() {
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { _: LifeCycleEvent, _: LifeCycleCoordinator -> }
            .use { coordinator ->
                assertEquals(TIMEOUT, coordinator.timeout)
            }
    }


}