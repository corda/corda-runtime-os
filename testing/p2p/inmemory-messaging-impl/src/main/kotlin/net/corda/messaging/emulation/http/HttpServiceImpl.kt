package net.corda.messaging.emulation.http

import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Component
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

@Component(service = [HttpService::class])
class HttpServiceImpl : HttpService {
    private val listeners = ConcurrentHashMap<String, (Any) -> Any?>()
    override fun send(
        uri: URI,
        data: Any,
    ): Any? {
        val endpoint = uri.path.split("/").last()
        val handler = listeners[endpoint] ?: throw CordaRuntimeException("Listener to $uri is not ready")
        return handler(data)
    }

    override fun listen(
        endpoint: String,
        handler: (Any) -> Any?,
    ) {
        listeners[endpoint.split("/").last()] = handler
    }

    override fun forget(
        endpoint: String,
    ) {
        listeners.remove(endpoint.split("/").last())
    }
}
