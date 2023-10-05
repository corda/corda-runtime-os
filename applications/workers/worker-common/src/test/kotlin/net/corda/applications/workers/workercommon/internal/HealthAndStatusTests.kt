package net.corda.applications.workers.workercommon.internal

import net.corda.applications.workers.workercommon.HTTP_HEALTH_ROUTE
import net.corda.applications.workers.workercommon.HTTP_STATUS_ROUTE
import net.corda.applications.workers.workercommon.Health
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.registry.CoordinatorStatus
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.rest.ResponseCode
import net.corda.web.api.Endpoint
import net.corda.web.api.HTTPMethod
import net.corda.web.api.WebContext
import net.corda.web.api.WebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class HealthAndStatusTests {
    private val lifecycleRegistry = mock<LifecycleRegistry>()
    private val webServer = mock<WebServer>()
    private val endpointCaptor = argumentCaptor<Endpoint>()

    @Test
    fun `registers status endpoint`() {
        doNothing().whenever(webServer).registerEndpoint(endpointCaptor.capture())
        Health.configure(webServer, lifecycleRegistry)

        assertThat(endpointCaptor.allValues.any {
            it.path == HTTP_STATUS_ROUTE && it.methodType == HTTPMethod.GET
        })
    }

    @Test
    fun `registers health endpoint`() {
        doNothing().whenever(webServer).registerEndpoint(endpointCaptor.capture())
        Health.configure(webServer, lifecycleRegistry)

        assertThat(endpointCaptor.allValues.any {
            it.path == HTTP_HEALTH_ROUTE && it.methodType == HTTPMethod.GET
        })
    }

    @Test
    fun `status returns OK when no DOWN or ERROR componets`() {
        whenever(lifecycleRegistry.componentWithStatus(listOf(LifecycleStatus.DOWN, LifecycleStatus.ERROR))).doReturn(emptyList())
        doNothing().whenever(webServer).registerEndpoint(endpointCaptor.capture())
        Health.configure(webServer, lifecycleRegistry)

        val handler = endpointCaptor.allValues.single { it.path == HTTP_STATUS_ROUTE }.webHandler
        val context = mock<WebContext>()
        handler.handle(context)
        verify(lifecycleRegistry).componentWithStatus(argThat {statuses: Collection<LifecycleStatus> ->
            statuses.size == 2 && statuses.containsAll(listOf(LifecycleStatus.DOWN, LifecycleStatus.ERROR))
        })
        verify(context).status(ResponseCode.OK)
    }

    @Test
    fun `health returns OK when no ERROR componets`() {
        whenever(lifecycleRegistry.componentWithStatus(listOf(LifecycleStatus.ERROR))).doReturn(emptyList())
        doNothing().whenever(webServer).registerEndpoint(endpointCaptor.capture())
        Health.configure(webServer, lifecycleRegistry)

        val handler = endpointCaptor.allValues.single { it.path == HTTP_HEALTH_ROUTE }.webHandler
        val context = mock<WebContext>()
        handler.handle(context)
        verify(lifecycleRegistry).componentWithStatus(argThat {statuses: Collection<LifecycleStatus> ->
            statuses.single() == LifecycleStatus.ERROR
        })
        verify(context).status(ResponseCode.OK)
    }

    @Test
    fun `OK returns NOT OK when ERROR or DOWN components`() {
        val registry = object : LifecycleRegistry {
            override fun componentStatus(): Map<LifecycleCoordinatorName, CoordinatorStatus> = emptyMap()
            override fun componentWithStatus(statuses: Collection<LifecycleStatus>): List<LifecycleCoordinatorName> {
                return listOf(LifecycleCoordinatorName("superman"))
            }

        }
        doNothing().whenever(webServer).registerEndpoint(endpointCaptor.capture())
        Health.configure(webServer, registry)

        val handler = endpointCaptor.allValues.single { it.path == HTTP_STATUS_ROUTE }.webHandler
        val context = mock<WebContext>()
        handler.handle(context)

        verify(context).status(ResponseCode.SERVICE_UNAVAILABLE)
    }

    @Test
    fun `health returns NOT OK when ERROR components`() {
        val registry = object : LifecycleRegistry {
            override fun componentStatus(): Map<LifecycleCoordinatorName, CoordinatorStatus> = emptyMap()
            override fun componentWithStatus(statuses: Collection<LifecycleStatus>): List<LifecycleCoordinatorName> {
                return listOf(LifecycleCoordinatorName("superman"))
            }

        }
        doNothing().whenever(webServer).registerEndpoint(endpointCaptor.capture())
        Health.configure(webServer, registry)

        val handler = endpointCaptor.allValues.single { it.path == HTTP_HEALTH_ROUTE }.webHandler
        val context = mock<WebContext>()
        handler.handle(context)

        verify(context).status(ResponseCode.SERVICE_UNAVAILABLE)
    }
}
