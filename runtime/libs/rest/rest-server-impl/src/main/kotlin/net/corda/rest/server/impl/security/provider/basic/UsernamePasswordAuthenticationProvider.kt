package net.corda.rest.server.impl.security.provider.basic

import net.corda.rest.security.AuthorizingSubject
import net.corda.rest.security.read.Password
import net.corda.rest.security.read.RestSecurityManager
import net.corda.rest.server.impl.security.provider.AuthenticationProvider
import net.corda.rest.server.impl.security.provider.credentials.AuthenticationCredentials
import net.corda.rest.server.impl.security.provider.credentials.tokens.UsernamePasswordAuthenticationCredentials
import net.corda.rest.server.impl.security.provider.scheme.AuthenticationScheme
import net.corda.rest.server.impl.security.provider.scheme.AuthenticationSchemeProvider
import net.corda.rest.server.impl.security.provider.scheme.AuthenticationSchemeProvider.Companion.REALM_KEY
import java.util.function.Supplier

/**
 * Simple AuthenticationProvider delegating username/password auth to RestSecurityManager
 */
internal class UsernamePasswordAuthenticationProvider(private val restSecurityManagerSupplier: Supplier<RestSecurityManager>) :
    AuthenticationProvider, AuthenticationSchemeProvider {
    override val authenticationMethod = AuthenticationScheme.BASIC

    override fun supports(credential: AuthenticationCredentials): Boolean {
        return credential is UsernamePasswordAuthenticationCredentials
    }

    override fun authenticate(credential: AuthenticationCredentials): AuthorizingSubject {
        if (credential !is UsernamePasswordAuthenticationCredentials) {
            throw IllegalArgumentException("Provider only supports username password authentication.")
        }

        return restSecurityManagerSupplier.get().authenticate(credential.username, Password(credential.password))
    }

    override fun provideParameters(): Map<String, String> {
        return mapOf(REALM_KEY to restSecurityManagerSupplier.get().id.value)
    }
}
