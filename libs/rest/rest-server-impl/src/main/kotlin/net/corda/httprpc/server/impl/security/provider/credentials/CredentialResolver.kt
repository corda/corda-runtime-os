package net.corda.httprpc.server.impl.security.provider.credentials

import net.corda.httprpc.server.impl.context.ClientRequestContext

/**
 * Resolves credentials from a Javalin Context
 */
internal interface CredentialResolver {
    fun resolve(context: ClientRequestContext): AuthenticationCredentials?
}
