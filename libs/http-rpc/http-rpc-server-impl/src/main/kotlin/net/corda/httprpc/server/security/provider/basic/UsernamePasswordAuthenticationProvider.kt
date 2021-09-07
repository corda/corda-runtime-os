package net.corda.httprpc.server.security.provider.basic

import net.corda.ext.internal.rpc.security.AuthorizingSubject
import net.corda.ext.internal.rpc.security.Password
import net.corda.ext.internal.rpc.security.RPCSecurityManager
import net.corda.httprpc.server.security.provider.AuthenticationProvider
import net.corda.httprpc.server.security.provider.credentials.AuthenticationCredentials
import net.corda.httprpc.server.security.provider.credentials.tokens.UsernamePasswordAuthenticationCredentials
import net.corda.httprpc.server.security.provider.scheme.AuthenticationScheme
import net.corda.httprpc.server.security.provider.scheme.AuthenticationSchemeProvider
import net.corda.httprpc.server.security.provider.scheme.AuthenticationSchemeProvider.Companion.REALM_KEY

/**
 * Simple AuthenticationProvider delegating username/password auth to RPCSecurityManager
 */
internal class UsernamePasswordAuthenticationProvider(private val rpcSecurityManager: RPCSecurityManager) : AuthenticationProvider, AuthenticationSchemeProvider {
    override val authenticationMethod = AuthenticationScheme.BASIC

    override fun supports(credential: AuthenticationCredentials): Boolean {
        return credential is UsernamePasswordAuthenticationCredentials
    }

    override fun authenticate(credential: AuthenticationCredentials): AuthorizingSubject {
        if (credential !is UsernamePasswordAuthenticationCredentials) {
            throw IllegalArgumentException("Provider only supports username password authentication.")
        }

        return rpcSecurityManager.authenticate(credential.username, Password(credential.password))
    }

    override fun provideParameters(): Map<String, String> {
        return mapOf(REALM_KEY to rpcSecurityManager.id.value)
    }
}
