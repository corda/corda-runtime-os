package net.corda.rest.client.auth.scheme

import kong.unirest.HttpRequest
import net.corda.rest.client.auth.RequestContext

/**
 * Represents an HTTP authentication scheme like Basic or Bearer
 */
interface AuthenticationScheme {
    fun authenticate(credentials: Any, request: HttpRequest<*>, context: RequestContext)
}
