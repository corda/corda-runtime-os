package net.corda.httprpc.client.auth.scheme

import kong.unirest.HttpRequest
import net.corda.httprpc.client.auth.RequestContext
import net.corda.httprpc.client.auth.credentials.BasicAuthCredentials

class BasicAuthenticationScheme : AuthenticationScheme {
    override fun authenticate(credentials: Any, request: HttpRequest<*>, context: RequestContext) {
        if (credentials !is BasicAuthCredentials) {
            throw IllegalArgumentException("This scheme does not support the provides credentials: $credentials")
        }

        request.basicAuth(credentials.username, credentials.password)
    }
}
