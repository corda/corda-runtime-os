package net.corda.applications.workers.workercommon

import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.core.util.Header
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.rest.ResponseCode
import net.corda.web.api.Endpoint
import net.corda.web.api.HTTPMethod
import net.corda.web.api.WebHandler
import net.corda.web.api.WebServer
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

object Health {
    private val logger = LoggerFactory.getLogger(Health::class.java)
    private val objectMapper = ObjectMapper()
    private val lastLogMessage = ConcurrentHashMap(mapOf(HTTP_HEALTH_ROUTE to "", HTTP_STATUS_ROUTE to ""))

    fun configure(webServer: WebServer, lifecycleRegistry: LifecycleRegistry) {
        val healthRouteHandler = WebHandler { context ->
            val unhealthyComponents = lifecycleRegistry.componentWithStatus(setOf(LifecycleStatus.ERROR))
            val healthy = unhealthyComponents.isEmpty()
            logIfDifferentFromLastMessage(
                HTTP_HEALTH_ROUTE,
                if (healthy)
                    "Status is healthy"
                else
                    "Status is unhealthy. The status of $unhealthyComponents has error.",
                healthy
            )
            context.status(if (healthy) ResponseCode.OK else ResponseCode.SERVICE_UNAVAILABLE)
            context.header(Header.CACHE_CONTROL, NO_CACHE)
            context
        }
        webServer.registerEndpoint(Endpoint(HTTPMethod.GET, HTTP_HEALTH_ROUTE, healthRouteHandler))

        val statusRouteHandler = WebHandler { context ->
            val notReadyComponents = lifecycleRegistry.componentWithStatus(setOf(LifecycleStatus.DOWN, LifecycleStatus.ERROR))
            val status = if (notReadyComponents.isEmpty()) {
                ResponseCode.OK
            } else {
                logIfDifferentFromLastMessage(
                    HTTP_STATUS_ROUTE,
                    "There are components with error or down state: $notReadyComponents.",
                    false
                )
                ResponseCode.SERVICE_UNAVAILABLE
            }
            context.status(status)
            context.result(objectMapper.writeValueAsString(lifecycleRegistry.componentStatus()))
            context.header(Header.CACHE_CONTROL, NO_CACHE)
            context
        }
        webServer.registerEndpoint(Endpoint(HTTPMethod.GET, HTTP_STATUS_ROUTE, statusRouteHandler))
    }



    private fun logIfDifferentFromLastMessage(route: String, logMessage: String, healthy: Boolean) {
        val lastLogMessage = lastLogMessage.put(route, logMessage)
        if (logMessage != lastLogMessage) {
            if (healthy && lastLogMessage == "") {
                // first time around, and we are healthy, so don't use WARN since this is normal
                logger.info(logMessage)
            } else {
                logger.warn(logMessage)
            }
        }
    }
}