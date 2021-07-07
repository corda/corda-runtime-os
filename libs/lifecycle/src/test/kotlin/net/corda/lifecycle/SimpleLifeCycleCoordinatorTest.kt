package net.corda.lifecycle

import net.corda.v5.base.util.contextLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.Logger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

internal class SimpleLifeCycleCoordinatorTest {

    companion object {

        const val BATCH_SIZE: Int = 128

        const val TIMEOUT: Long = 2000L

        val logger: Logger = contextLogger()
    }

    interface PostEvent : LifeCycleEvent

    interface ThrowException : LifeCycleEvent

    @Test
    fun burstEvents() {
        val n = BATCH_SIZE * 2
        val startLatch = CountDownLatch(1)
        val countDownLatch =
            CountDownLatch(n)  // Used to test all posted events are processed when coordinator stopped.
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
                    fail("Unexpected $event!")
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
        val n = BATCH_SIZE * 2
        val startLatch = CountDownLatch(1)
        val countDownLatch =
            CountDownLatch(n)  // Used to test all posted events are processed when coordinator stopped.
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

    @ParameterizedTest
    @ValueSource(ints = [5])
    fun postHandledErrorEvent(n: Int) {
        var stopLatch = CountDownLatch(1)
        val expectedException = Exception("expected exception")
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { event: LifeCycleEvent, coordinator: LifeCycleCoordinator ->
            when (event) {
                is StartEvent -> {
                    stopLatch = CountDownLatch(1)
                }
                is ThrowException -> {
                    throw expectedException
                }
                is ErrorEvent -> {
                    assertEquals(expectedException, event.cause)
                    event.isHandled = true
                    coordinator.postEvent(object : PostEvent {})
                }
                is PostEvent -> {
                    coordinator.stop()
                }
                is StopEvent -> {
                    assertTrue(stopLatch.count > 0)
                    stopLatch.countDown()
                }
            }
        }.use { coordinator ->
            for (i in 0..n) {
                coordinator.start()
                coordinator.postEvent(object : ThrowException {})
                assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [5])
    fun postHandledButRethrowErrorEvent(n: Int) {
        var stopLatch = CountDownLatch(2)
        val expectedException = Exception("expected exception")
        val unexpectedException = Exception("unexpected exception")
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { event: LifeCycleEvent, _: LifeCycleCoordinator ->
            when (event) {
                is StartEvent -> {
                    stopLatch = CountDownLatch(2)
                }
                is ThrowException -> {
                    throw expectedException
                }
                is ErrorEvent -> {
                    when (event.cause) {
                        expectedException -> {
                            stopLatch.countDown()
                            event.isHandled = true
                            throw unexpectedException
                        }
                        else -> {
                            fail("Unexpected ${event.cause}!")
                        }
                    }
                }
                is StopEvent -> {
                    stopLatch.countDown()
                    assertTrue(stopLatch.count == 0L)
                }
            }
        }.use { coordinator ->
            for (i in 0..n) {
                coordinator.start()
                coordinator.postEvent(object : ThrowException {})
                assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            }
        }
    }


    @ParameterizedTest
    @ValueSource(ints = [5])
    fun postUnhandledErrorEvent(n: Int) {
        var stopLatch = CountDownLatch(2)
        val expectedException = Exception("expected exception")
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { event: LifeCycleEvent, _: LifeCycleCoordinator ->
            when (event) {
                is StartEvent -> {
                    stopLatch = CountDownLatch(2)
                }
                is ThrowException -> {
                    throw expectedException
                }
                is ErrorEvent -> {
                    when (event.cause) {
                        expectedException -> {
                            stopLatch.countDown()
                        }
                        else -> {
                            fail("Unexpected ${event.cause}!")
                        }
                    }
                }
                is StopEvent -> {
                    stopLatch.countDown()
                    assertTrue(stopLatch.count == 0L)
                }
            }
        }.use { coordinator ->
            for(i in 0 .. n) {
                coordinator.start()
                coordinator.postEvent(object : ThrowException {})
                assertTrue(stopLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
            }
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

    @ParameterizedTest
    @ValueSource(ints = [5])
    fun startAndStopLoop(n: Int) {
        val startLatch = CountDownLatch(n)
        val stopLatch = CountDownLatch(n)
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { event: LifeCycleEvent, _: LifeCycleCoordinator ->
            logger.debug("processEvent $event")
            when (event) {
                is StartEvent -> {
                    startLatch.countDown()
                }
                is StopEvent -> {
                    stopLatch.countDown()
                }
            }
        }.use { coordinator ->
            for (i in 0..n) {
                assertFalse(coordinator.isRunning)
                coordinator.start()
                assertTrue(coordinator.isRunning)
                coordinator.stop()
            }
            assertTrue(startLatch.await(n * TIMEOUT, TimeUnit.MILLISECONDS))
            assertTrue(stopLatch.await(n * TIMEOUT, TimeUnit.MILLISECONDS))
        }
    }

}