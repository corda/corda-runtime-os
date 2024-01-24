package net.corda.rest.server.impl.security.provider

import net.corda.rest.authorization.AuthorizingSubject
import net.corda.rest.server.impl.security.provider.credentials.AuthenticationCredentials

/**
 * Supports generating an AuthorizingSubject for a supported and valid credential object
 */
internal interface AuthenticationProvider {
    fun supports(credential: AuthenticationCredentials): Boolean
    fun authenticate(credential: AuthenticationCredentials): AuthorizingSubject
}
