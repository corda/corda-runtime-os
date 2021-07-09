package net.corda.lifecycle

import net.corda.v5.base.util.contextLogger
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.Logger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

// Order imposed to lighten pressure in CI.
// @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class SimpleLifeCycleCoordinatorTest {

    companion object {

        const val BATCH_SIZE: Int = 128

        const val TIMEOUT: Long = 1000L

        val logger: Logger = contextLogger()
    }

    interface PostEvent : LifeCycleEvent

    interface ThrowException : LifeCycleEvent

    @Order(1)
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
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
            Thread.yield()
            startLatch.await()
            for (i in 0 until n) {
                coordinator.postEvent(object : PostEvent {})
            }
            coordinator.stop()
            Thread.yield()
            stopLatch.await()
            Thread.yield()
            countDownLatch.await()
        }
    }

    @Order(2)
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
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
            Thread.yield()
            startLatch.await()
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
            Thread.yield()
            stopLatch.await()
        }
        assertTrue(n > countDownLatch.count)
    }

    @Order(3)
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    fun cancelTimer() {
        val startLatch = CountDownLatch(1)
        val key = "kill_me_softly"
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
            Thread.yield()
            startLatch.await()
            coordinator.setTimer(key, TIMEOUT / 2) {
                object : TimerEvent {
                    override val key: String
                        get() = key
                }
            }
            coordinator.cancelTimer(key)
        }
    }

    @Order(4)
    @Test
    fun getBatchSize() {
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { _: LifeCycleEvent, _: LifeCycleCoordinator -> }
            .use { coordinator ->
                assertEquals(BATCH_SIZE, coordinator.batchSize)
            }
    }

    @Order(5)
    @Test
    fun getTimeout() {
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { _: LifeCycleEvent, _: LifeCycleCoordinator -> }
            .use { coordinator ->
                assertEquals(TIMEOUT, coordinator.timeout)
            }
    }

    @Order(6)
    @ParameterizedTest
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
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
                Thread.yield()
                stopLatch.await()
            }
        }
    }

    @Order(7)
    @ParameterizedTest
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
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
                Thread.yield()
                stopLatch.await()
            }
        }
    }

    @Order(8)
    @ParameterizedTest
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @ValueSource(ints = [5])
    fun postUnhandledErrorEvent(n: Int) {
        var stopLatch = CountDownLatch(1)
        val expectedException = Exception("expected exception")
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { event: LifeCycleEvent, _: LifeCycleCoordinator ->
            when (event) {
                is StartEvent -> {
                    stopLatch = CountDownLatch(1)
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
                            logger.error("Unexpected ${event.cause}!", event.cause)
                            fail("Unexpected ${event.cause}!")
                        }
                    }
                }
                is StopEvent -> {
                    stopLatch.countDown()
                }
            }
        }.use { coordinator ->
            for(i in 0 .. n) {
                coordinator.start()
                coordinator.postEvent(object : ThrowException {})
                stopLatch.countDown()
                stopLatch.await()
            }
        }
    }

    @Order(9)
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
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
            Thread.yield()
            startLatch.await()
            coordinator.setTimer(key, TIMEOUT / 2) {
                object : TimerEvent {
                    override val key: String
                        get() = key
                }
            }
            Thread.yield()
            timerLatch.await()
        }
    }

    @Order(10)
    @ParameterizedTest
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
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
                Thread.yield()
                assertTrue(coordinator.isRunning)
                coordinator.stop()
                Thread.yield()
            }
            startLatch.await()
            stopLatch.await()
        }
    }

}