package net.corda.applications.workers.workercommon.internal

import net.corda.applications.workers.workercommon.WorkerMonitor
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.registry.CoordinatorStatus
import net.corda.lifecycle.registry.LifecycleRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URL

/** Tests of [WorkerMonitorImpl]. */
class WorkerMonitorImplTests {
    @Test
    fun `worker is considered healthy and ready if there are no components in the lifecycle registry`() {
        val healthMonitor = startHealthMonitor(emptyMap())
        val (healthyCode, readyCode) = getHealthAndReadinessCodes(healthMonitor.port!!)

        assertEquals(HTTP_OK_CODE, healthyCode)
        assertEquals(HTTP_OK_CODE, readyCode)

        healthMonitor.stop()
    }

    @Test
    fun `worker is considered healthy if all components in the lifecycle registry are up or down`() {
        val componentStatuses = mapOf(
            createComponentStatus(LifecycleStatus.UP),
            createComponentStatus(LifecycleStatus.DOWN)
        )
        val healthMonitor = startHealthMonitor(componentStatuses)
        val (healthyCode, _) = getHealthAndReadinessCodes(healthMonitor.port!!)

        assertEquals(HTTP_OK_CODE, healthyCode)

        healthMonitor.stop()
    }

    @Test
    fun `worker is considered unhealthy if any components in the lifecycle registry are errored`() {
        val componentStatuses = mapOf(
            createComponentStatus(LifecycleStatus.UP),
            createComponentStatus(LifecycleStatus.DOWN),
            createComponentStatus(LifecycleStatus.ERROR)
        )
        val healthMonitor = startHealthMonitor(componentStatuses)
        val (healthyCode, _) = getHealthAndReadinessCodes(healthMonitor.port!!)

        assertEquals(HTTP_SERVICE_UNAVAILABLE_CODE, healthyCode)

        healthMonitor.stop()
    }

    @Test
    fun `worker is considered ready if all components in the lifecycle registry are up`() {
        val componentStatuses = mapOf(
            createComponentStatus(LifecycleStatus.UP)
        )
        val healthMonitor = startHealthMonitor(componentStatuses)
        val (_, readyCode) = getHealthAndReadinessCodes(healthMonitor.port!!)

        assertEquals(HTTP_OK_CODE, readyCode)

        healthMonitor.stop()
    }

    @Test
    fun `worker is considered not ready if any components are down`() {
        val componentStatuses = mapOf(
            createComponentStatus(LifecycleStatus.UP),
            createComponentStatus(LifecycleStatus.DOWN)
        )
        val healthMonitor = startHealthMonitor(componentStatuses)
        val (_, readyCode) = getHealthAndReadinessCodes(healthMonitor.port!!)

        assertEquals(HTTP_SERVICE_UNAVAILABLE_CODE, readyCode)

        healthMonitor.stop()
    }

    @Test
    fun `worker is considered not ready if any components are errored`() {
        val componentStatuses = mapOf(
            createComponentStatus(LifecycleStatus.UP),
            createComponentStatus(LifecycleStatus.ERROR)
        )
        val healthMonitor = startHealthMonitor(componentStatuses)
        val (_, readyCode) = getHealthAndReadinessCodes(healthMonitor.port!!)

        assertEquals(HTTP_SERVICE_UNAVAILABLE_CODE, readyCode)

        healthMonitor.stop()
    }

    /** Creates a pair of [LifecycleCoordinatorName], [CoordinatorStatus] for a given [status]. */
    private fun createComponentStatus(status: LifecycleStatus): Pair<LifecycleCoordinatorName, CoordinatorStatus> {
        val name = LifecycleCoordinatorName("")
        return name to CoordinatorStatus(name, status, "")
    }

    /** Creates and starts a [WorkerMonitor] that wraps a [LifecycleRegistry] with the given [componentStatuses]. */
    private fun startHealthMonitor(componentStatuses: Map<LifecycleCoordinatorName, CoordinatorStatus>): WorkerMonitor {
        val lifecycleRegistry = TestLifecycleRegistry(componentStatuses)
        val healthMonitor = WorkerMonitorImpl(lifecycleRegistry)
        healthMonitor.listen(0)
        return healthMonitor
    }

    /** Retrieves the HTTP codes of the health and readiness endpoints of a running [WorkerMonitor]. */
    private fun getHealthAndReadinessCodes(port: Int): Pair<Int, Int> {
        val responseCodeHealthy = (URL("http://localhost:$port$HTTP_HEALTH_ROUTE").openConnection() as HttpURLConnection).responseCode
        val responseCodeReady = (URL("http://localhost:$port$HTTP_STATUS_ROUTE").openConnection() as HttpURLConnection).responseCode
        return responseCodeHealthy to responseCodeReady
    }
}

/** A test [LifecycleRegistry] implementation with a hardcoded map of [componentStatuses]. */
private class TestLifecycleRegistry(private val componentStatuses: Map<LifecycleCoordinatorName, CoordinatorStatus>) :
    LifecycleRegistry {
    override fun componentStatus(): Map<LifecycleCoordinatorName, CoordinatorStatus> = componentStatuses
}