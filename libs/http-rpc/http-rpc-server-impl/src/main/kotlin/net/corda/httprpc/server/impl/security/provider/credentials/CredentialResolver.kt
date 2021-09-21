package net.corda.httprpc.server.impl.security.provider.credentials

import io.javalin.http.Context

/**
 * Resolves credentials from a Javalin Context
 */
internal interface CredentialResolver {
    fun resolve(context: Context): AuthenticationCredentials?
}
