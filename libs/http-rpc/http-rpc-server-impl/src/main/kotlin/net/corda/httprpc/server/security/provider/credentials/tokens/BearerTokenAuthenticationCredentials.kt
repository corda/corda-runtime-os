package net.corda.httprpc.server.security.provider.credentials.tokens

import net.corda.httprpc.server.security.provider.credentials.AuthenticationCredentials

internal data class BearerTokenAuthenticationCredentials(val token: String) : AuthenticationCredentials
