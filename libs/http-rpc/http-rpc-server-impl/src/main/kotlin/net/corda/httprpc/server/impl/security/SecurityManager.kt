package net.corda.httprpc.server.impl.security

import net.corda.httprpc.rpc.proxies.RpcAuthHelper
import net.corda.httprpc.security.AuthorizingSubject
import net.corda.httprpc.server.impl.security.provider.AuthenticationProvider
import net.corda.httprpc.server.impl.security.provider.credentials.AuthenticationCredentials
import net.corda.httprpc.server.impl.security.provider.scheme.AuthenticationSchemeProvider
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import java.lang.reflect.Method
import net.corda.httprpc.exception.NotAuthenticatedException

interface HttpRpcSecurityManager {
    fun authenticate(credential: AuthenticationCredentials): AuthorizingSubject
    fun getSchemeProviders(): Set<AuthenticationSchemeProvider>
    fun authorize(authenticatedUser: AuthorizingSubject, method: Method): Boolean
}

internal class SecurityManagerRPCImpl(private val providers: Set<AuthenticationProvider>) : HttpRpcSecurityManager {
    private companion object {
        private val log = contextLogger()
    }

    override fun authenticate(credential: AuthenticationCredentials): AuthorizingSubject {
        var lastException: NotAuthenticatedException? = null
        for (provider in providers) {
            if (provider.supports(credential)) {
                lastException = try {
                    return provider.authenticate(credential)
                } catch (e: NotAuthenticatedException) {
                    e
                }
            }
        }

        throw lastException ?: NotAuthenticatedException("Unable to authenticate request.")
    }

    override fun getSchemeProviders(): Set<AuthenticationSchemeProvider> {
        return providers.filterIsInstance(AuthenticationSchemeProvider::class.java).toSet()
    }

    override fun authorize(authenticatedUser: AuthorizingSubject, method: Method): Boolean {
        log.debug {
            """Authorizing user: "${authenticatedUser.principal}" on method: "${
                RpcAuthHelper.methodFullName(
                    method
                )
            }""""
        }
        return authenticatedUser.isPermitted(RpcAuthHelper.methodFullName(method)).also {
            log.trace {
                """Authorizing user: "${authenticatedUser.principal}" on method: "${
                    RpcAuthHelper.methodFullName(
                        method
                    )
                }" completed. Result: $it"""
            }
        }
    }
}
