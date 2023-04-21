package net.corda.rest.client.auth.scheme

import kong.unirest.HeaderNames.AUTHORIZATION
import kong.unirest.HttpRequest
import net.corda.rest.client.auth.RequestContext
import net.corda.rest.client.auth.credentials.BearerTokenCredentials

internal class BearerTokenAuthenticationScheme : AuthenticationScheme {
    override fun authenticate(credentials: Any, request: HttpRequest<*>, context: RequestContext) {
        if (credentials !is BearerTokenCredentials) {
            throw IllegalArgumentException("This scheme does not support the provides credentials: $credentials")
        }

        request.header(AUTHORIZATION, "Bearer ${credentials.token}")
    }
}
