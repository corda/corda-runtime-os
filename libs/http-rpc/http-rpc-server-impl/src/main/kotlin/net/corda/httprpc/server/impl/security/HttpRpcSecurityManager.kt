package net.corda.httprpc.server.impl.security

import net.corda.httprpc.security.AuthorizingSubject
import net.corda.httprpc.server.impl.security.provider.AuthenticationProvider
import net.corda.httprpc.server.impl.security.provider.credentials.AuthenticationCredentials
import net.corda.httprpc.server.impl.security.provider.scheme.AuthenticationSchemeProvider
import javax.security.auth.login.FailedLoginException

interface HttpRpcSecurityManager {
    fun authenticate(credential: AuthenticationCredentials): AuthorizingSubject
    fun getSchemeProviders(): Set<AuthenticationSchemeProvider>
}

internal class SecurityManagerRPCImpl(private val providers: Set<AuthenticationProvider>) : HttpRpcSecurityManager {

    override fun authenticate(credential: AuthenticationCredentials): AuthorizingSubject {
        var lastException: FailedLoginException? = null
        for (provider in providers) {
            if (provider.supports(credential)) {
                lastException = try {
                    return provider.authenticate(credential)
                } catch (e: FailedLoginException) {
                    e
                }
            }
        }

        throw lastException ?: FailedLoginException("Unable to authenticate request.")
    }

    override fun getSchemeProviders(): Set<AuthenticationSchemeProvider> {
        return providers.filterIsInstance(AuthenticationSchemeProvider::class.java).toSet()
    }
}
