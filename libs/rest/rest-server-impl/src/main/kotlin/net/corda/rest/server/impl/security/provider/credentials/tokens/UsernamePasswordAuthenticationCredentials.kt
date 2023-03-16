package net.corda.rest.server.impl.security.provider.credentials.tokens

import net.corda.rest.server.impl.security.provider.credentials.AuthenticationCredentials

/**
 * Represents user credentials for a Basic HTTP Authentication
 */
internal data class UsernamePasswordAuthenticationCredentials(val username: String, val password: String) :
    AuthenticationCredentials
