package net.corda.httprpc.server.security.provider.bearer

import net.corda.httprpc.security.read.AuthorizingSubject
import net.corda.httprpc.server.security.provider.AuthenticationProvider
import net.corda.httprpc.server.security.provider.credentials.AuthenticationCredentials
import net.corda.httprpc.server.security.provider.credentials.tokens.BearerTokenAuthenticationCredentials

internal abstract class BearerTokenAuthenticationProvider : AuthenticationProvider {
    override fun supports(credential: AuthenticationCredentials): Boolean {
        return credential is BearerTokenAuthenticationCredentials
    }

    override fun authenticate(credential: AuthenticationCredentials): AuthorizingSubject {
        if (credential !is BearerTokenAuthenticationCredentials) {
            throw IllegalArgumentException("Provider only supports bearer tokens.")
        }

        return doAuthenticate(credential)
    }

    protected abstract fun doAuthenticate(credential: BearerTokenAuthenticationCredentials): AuthorizingSubject
}
