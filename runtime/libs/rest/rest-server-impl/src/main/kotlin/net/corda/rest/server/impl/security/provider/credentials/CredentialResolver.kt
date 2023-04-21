package net.corda.rest.server.impl.security.provider.credentials

import net.corda.rest.server.impl.context.ClientRequestContext

/**
 * Resolves credentials from a Javalin Context
 */
internal interface CredentialResolver {
    fun resolve(context: ClientRequestContext): AuthenticationCredentials?
}
