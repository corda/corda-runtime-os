package net.corda.applications.workers.workercommon.internal

import io.javalin.Javalin
import net.corda.applications.workers.workercommon.WorkerMonitor
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.registry.CoordinatorStatus
import net.corda.lifecycle.registry.LifecycleRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.web.server.JavalinServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/** Tests of [WorkerMonitorImpl]. */
class WorkerMonitorImplTests {

    private val lifecycleCoordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) }.doReturn(lifecycleCoordinator)
    }

    private val webServer = JavalinServer(lifecycleCoordinatorFactory) { Javalin.create() }
    private val port = ServerSocket(0).use {
        it.localPort
    }

    @BeforeEach
    fun setupServer() {
        webServer.start(port)
    }

    @AfterEach
    fun teardownServer() {
        webServer.stop()
    }
    @Test
    fun `worker is considered healthy and ready if there are no components in the lifecycle registry`() {
        startHealthMonitor(emptyMap())
        val (healthyCode, readyCode) = getHealthAndReadinessCodes(port)

        assertEquals(HTTP_OK_CODE, healthyCode)
        assertEquals(HTTP_OK_CODE, readyCode)
    }

    @Test
    fun `worker is considered healthy if all components in the lifecycle registry are up or down`() {
        val componentStatuses = mapOf(
            createComponentStatus(LifecycleStatus.UP),
            createComponentStatus(LifecycleStatus.DOWN)
        )
        startHealthMonitor(componentStatuses)
        val (healthyCode, _) = getHealthAndReadinessCodes(port)

        assertEquals(HTTP_OK_CODE, healthyCode)
    }

    @Test
    fun `worker is considered unhealthy if any components in the lifecycle registry are errored`() {
        val componentStatuses = mapOf(
            createComponentStatus(LifecycleStatus.UP),
            createComponentStatus(LifecycleStatus.DOWN),
            createComponentStatus(LifecycleStatus.ERROR)
        )
        startHealthMonitor(componentStatuses)
        val (healthyCode, _) = getHealthAndReadinessCodes(port)

        assertEquals(HTTP_SERVICE_UNAVAILABLE_CODE, healthyCode)
    }

    @Test
    fun `worker is considered ready if all components in the lifecycle registry are up`() {
        val componentStatuses = mapOf(
            createComponentStatus(LifecycleStatus.UP)
        )
        startHealthMonitor(componentStatuses)
        val (_, readyCode) = getHealthAndReadinessCodes(port)

        assertEquals(HTTP_OK_CODE, readyCode)
    }

    @Test
    fun `worker is considered not ready if any components are down`() {
        val componentStatuses = mapOf(
            createComponentStatus(LifecycleStatus.UP),
            createComponentStatus(LifecycleStatus.DOWN)
        )
        startHealthMonitor(componentStatuses)
        val (_, readyCode) = getHealthAndReadinessCodes(port)

        assertEquals(HTTP_SERVICE_UNAVAILABLE_CODE, readyCode)
    }

    @Test
    fun `worker is considered not ready if any components are errored`() {
        val componentStatuses = mapOf(
            createComponentStatus(LifecycleStatus.UP),
            createComponentStatus(LifecycleStatus.ERROR)
        )
        startHealthMonitor(componentStatuses)
        val (_, readyCode) = getHealthAndReadinessCodes(port)

        assertEquals(HTTP_SERVICE_UNAVAILABLE_CODE, readyCode)
    }

    /** Creates a pair of [LifecycleCoordinatorName], [CoordinatorStatus] for a given [status]. */
    private fun createComponentStatus(status: LifecycleStatus): Pair<LifecycleCoordinatorName, CoordinatorStatus> {
        val name = LifecycleCoordinatorName("")
        return name to CoordinatorStatus(name, status, "")
    }

    /** Creates and starts a [WorkerMonitor] that wraps a [LifecycleRegistry] with the given [componentStatuses]. */
    private fun startHealthMonitor(componentStatuses: Map<LifecycleCoordinatorName, CoordinatorStatus>): WorkerMonitor {
        val lifecycleRegistry = TestLifecycleRegistry(componentStatuses)
        val healthMonitor = WorkerMonitorImpl(lifecycleRegistry, webServer)
        healthMonitor.registerEndpoints(this.javaClass.simpleName)
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