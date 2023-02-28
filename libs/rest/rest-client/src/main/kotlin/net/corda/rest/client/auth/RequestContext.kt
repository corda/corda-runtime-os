package net.corda.rest.client.auth

import net.corda.rest.client.auth.scheme.AuthenticationScheme
import net.corda.rest.client.config.AuthenticationConfig

/**
 * Analog of Apache HTTP's HttpContext can be extended to support storing state for challenge response based schemes
 */
class RequestContext internal constructor(val credentials: Any, val authenticationScheme: AuthenticationScheme) {
    companion object {
        internal fun fromAuthenticationConfig(config: AuthenticationConfig): RequestContext {
            return RequestContext(config.getCredentialsProvider().getCredentials(), config.createScheme())
        }
    }
}
