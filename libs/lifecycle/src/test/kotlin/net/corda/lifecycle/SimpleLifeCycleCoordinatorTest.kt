package net.corda.lifecycle

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class SimpleLifeCycleCoordinatorTest {

    companion object {

        const val BATCH_SIZE: Int = 128

        const val TIMEOUT: Long = 1000L

        val logger: Logger = LoggerFactory.getLogger(SimpleLifeCycleCoordinatorTest::class.java)
    }

    interface PostEvent : LifeCycleEvent

    @Test
    fun cancelTimer() {
        val startLatch = CountDownLatch(1)
        val key = "kill_me_softly"
        val timerLatch = CountDownLatch(1)
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { lifeCycleEvent: LifeCycleEvent ->
            logger.debug("processEvent $lifeCycleEvent")
            when (lifeCycleEvent) {
                is StartEvent -> startLatch.countDown()
                is TimerEvent -> {
                    fail("${lifeCycleEvent}.${lifeCycleEvent.key} cancelled but executed! ")
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
    fun postEvent() {
        val postLatch = CountDownLatch(1)
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { lifeCycleEvent: LifeCycleEvent ->
            when (lifeCycleEvent) {
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
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { lifeCycleEvent: LifeCycleEvent ->
            logger.debug("processEvent $lifeCycleEvent")
            when (lifeCycleEvent) {
                is StartEvent -> startLatch.countDown()
                is TimerEvent -> {
                    timerLatch.countDown()
                    assertEquals(key, lifeCycleEvent.key)
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
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { lifeCycleEvent: LifeCycleEvent ->
            logger.debug("processEvent $lifeCycleEvent")
            when (lifeCycleEvent) {
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
        val startLatch = CountDownLatch(1)
        val stopLatch = CountDownLatch(1)
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { lifeCycleEvent: LifeCycleEvent ->
            logger.debug("processEvent $lifeCycleEvent")
            when (lifeCycleEvent) {
                is StartEvent -> startLatch.countDown()
                is StopEvent -> stopLatch.countDown()
            }
        }.use { coordinator ->
            coordinator.start()
            assertTrue(startLatch.await(TIMEOUT * 2, TimeUnit.MILLISECONDS))
            assertTrue(coordinator.isRunning)
            coordinator.stop()
            assertTrue(stopLatch.await(TIMEOUT * 2, TimeUnit.MILLISECONDS))
            assertFalse(coordinator.isRunning)
        }
    }

    @Test
    fun getBatchSize() {
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { }.use { coordinator ->
            assertEquals(BATCH_SIZE, coordinator.batchSize)
        }
    }

    @Test
    fun getTimeout() {
        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { }.use { coordinator ->
            assertEquals(TIMEOUT, coordinator.timeout)
        }
    }
}