package net.corda.testing.driver.sandbox

import net.corda.web.api.Endpoint
import net.corda.web.api.WebServer
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

@Suppress("unused")
@Component(service = [ WebServer::class ], property = [ DRIVER_SERVICE ])
@ServiceRanking(DRIVER_SERVICE_RANKING)
class WebServerImpl : WebServer {
    override val port: Int
        get() = 0

    override val endpoints: Set<Endpoint>
        get() = emptySet()

    override fun start(port: Int) {
    }

    override fun stop() {
    }

    override fun registerEndpoint(endpoint: Endpoint) {
    }

    override fun removeEndpoint(endpoint: Endpoint) {
    }
}
