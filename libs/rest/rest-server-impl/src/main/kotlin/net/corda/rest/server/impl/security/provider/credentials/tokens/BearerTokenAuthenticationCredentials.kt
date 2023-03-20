package net.corda.rest.server.impl.security.provider.credentials.tokens

import net.corda.rest.server.impl.security.provider.credentials.AuthenticationCredentials

internal data class BearerTokenAuthenticationCredentials(val token: String) : AuthenticationCredentials
