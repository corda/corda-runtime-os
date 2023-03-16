package net.corda.rest.client.auth.scheme

import kong.unirest.HttpRequest
import net.corda.rest.client.auth.RequestContext

internal object NoopAuthenticationScheme : AuthenticationScheme {
    override fun authenticate(credentials: Any, request: HttpRequest<*>, context: RequestContext) {
    }
}
