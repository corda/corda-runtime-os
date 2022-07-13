package net.corda.httprpc.server.impl.security.provider.credentials

import io.javalin.core.util.Header.AUTHORIZATION
import net.corda.httprpc.server.impl.context.ClientRequestContext
import net.corda.httprpc.server.impl.security.provider.credentials.tokens.BearerTokenAuthenticationCredentials
import net.corda.httprpc.server.impl.security.provider.credentials.tokens.UsernamePasswordAuthenticationCredentials
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import javax.security.auth.login.FailedLoginException

internal class DefaultCredentialResolver : CredentialResolver {
    companion object {
        private val TOKEN_PATTERN = "^Bearer (?<token>[a-zA-Z0-9-._~+/]+=*)$".toRegex()
        private val log = contextLogger()
    }

    override fun resolve(context: ClientRequestContext): AuthenticationCredentials? {
        val authorization = context.header(AUTHORIZATION) ?: return null

        return when {
            authorization.startsWith("bearer", true) -> {
                log.trace { "Get bearer token auth credentials." }
                val match =
                    TOKEN_PATTERN.matchEntire(authorization) ?: throw FailedLoginException("Malformed Bearer token.")
                BearerTokenAuthenticationCredentials(match.groupValues[1])
            }
            context.basicAuthCredentialsExist() -> {
                log.trace { "Get basic auth credentials." }
                val creds = context.basicAuthCredentials()
                UsernamePasswordAuthenticationCredentials(creds.username, creds.password)
            }
            else -> {
                null
            }
        }
    }
}
