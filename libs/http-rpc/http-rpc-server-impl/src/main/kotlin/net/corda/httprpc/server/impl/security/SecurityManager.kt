package net.corda.httprpc.server.impl.security

import net.corda.httprpc.security.AuthorizingSubject
import net.corda.httprpc.server.impl.security.provider.AuthenticationProvider
import net.corda.httprpc.server.impl.security.provider.credentials.AuthenticationCredentials
import net.corda.httprpc.server.impl.security.provider.scheme.AuthenticationSchemeProvider
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import javax.security.auth.login.FailedLoginException

interface HttpRpcSecurityManager {
    fun authenticate(credential: AuthenticationCredentials): AuthorizingSubject
    fun getSchemeProviders(): Set<AuthenticationSchemeProvider>
    fun authorize(authenticatedUser: AuthorizingSubject, path: String, httpVerb: String): Boolean
}

internal class SecurityManagerRPCImpl(private val providers: Set<AuthenticationProvider>) : HttpRpcSecurityManager {
    private companion object {
        private val log = contextLogger()
    }

    override fun authenticate(credential: AuthenticationCredentials): AuthorizingSubject {
        var lastException: FailedLoginException? = null
        for (provider in providers) {
            if (provider.supports(credential)) {
                try {
                    return provider.authenticate(credential)
                } catch (e: FailedLoginException) {
                    lastException = e
                }
            }
        }

        throw lastException ?: FailedLoginException("Unable to authenticate request.")
    }

    override fun getSchemeProviders(): Set<AuthenticationSchemeProvider> {
        return providers.filterIsInstance(AuthenticationSchemeProvider::class.java).toSet()
    }

    override fun authorize(authenticatedUser: AuthorizingSubject, path: String, httpVerb: String): Boolean {
        log.debug {
            """Authorizing user: "${authenticatedUser.principal}" for $httpVerb request to $path""""
        }
        return authenticatedUser.isPermitted(path, httpVerb).also {
            log.trace {
                """Authorizing user: "${authenticatedUser.principal}" for $httpVerb request to $path completed. Result: $it"""
            }
        }
    }
}
