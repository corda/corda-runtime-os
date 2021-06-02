package net.corda.lifecycle

import org.junit.jupiter.api.Assertions.assertTrue
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
    fun isRunning() {
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
            coordinator.start()
            assertTrue(startLatch.await(0, TimeUnit.MILLISECONDS))
        }

    }

    @Test
    fun stop() {
    }

    @Test
    fun getBatchSize() {
    }

    @Test
    fun getLifeCycleProcessor() {
    }

    @Test
    fun getTimeout() {
    }
}