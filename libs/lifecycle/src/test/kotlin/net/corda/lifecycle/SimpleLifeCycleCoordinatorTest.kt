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

        const val TIMEOUT: Long = 10000L

        val logger: Logger = LoggerFactory.getLogger(SimpleLifeCycleCoordinatorTest::class.java)
    }

    @Test
    fun cancelTimer() {
    }

    @Test
    fun postEvent() {
    }

    @Test
    fun setTimer() {
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