package net.corda.applications.workers.workercommon.test

import net.corda.applications.workers.healthprovider.HealthProvider
import net.corda.applications.workers.workercommon.Worker
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactoryImpl
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkerTests {

    @Test
    fun `worker sets itself as healthy but not ready at startup`() {
        val healthProvider = TestHealthProvider()

        val worker = object : Worker(SmartConfigFactoryImpl(), healthProvider) {
            override fun startup(healthProvider: HealthProvider, workerConfig: SmartConfig) = Unit
        }
        worker.startup(arrayOf())

        assertTrue(healthProvider.isHealthy)
        assertFalse(healthProvider.isReady)
    }

    @Test
    fun `worker sets itself as unhealthy and not ready if it throws an uncaught exception`() {
        val healthProvider = TestHealthProvider()

        val worker = object : Worker(SmartConfigFactoryImpl(), healthProvider) {
            override fun startup(healthProvider: HealthProvider, workerConfig: SmartConfig) = throw Exception()
        }
        worker.startup(arrayOf())

        assertFalse(healthProvider.isHealthy)
        assertFalse(healthProvider.isReady)
    }
}

/** Tracks health and readiness via the [isHealthy] and [isReady] variables. */
private class TestHealthProvider : HealthProvider {
    var isHealthy = false
    var isReady = false

    override fun setHealthy() {
        isHealthy = true
    }

    override fun setNotHealthy() {
        isHealthy = false
    }

    override fun setReady() {
        isReady = true
    }

    override fun setNotReady() {
        isReady = false
    }

}