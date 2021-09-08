package net.corda.httprpc.server.security.provider

import net.corda.httprpc.server.security.AuthorizingSubject
import net.corda.httprpc.server.security.provider.credentials.AuthenticationCredentials

/**
 * Supports generating an AuthorizingSubject for a supported and valid credential object
 */
internal interface AuthenticationProvider {
    fun supports(credential: AuthenticationCredentials): Boolean
    fun authenticate(credential: AuthenticationCredentials): AuthorizingSubject
}
