package net.corda.httprpc.client.config

import net.corda.httprpc.client.auth.credentials.BearerTokenProvider

data class RestClientConfig internal constructor(
    val enableSSL: Boolean,
    val minimumServerProtocolVersion: Int,
    val authenticationConfig: AuthenticationConfig
) {
    constructor() : this(false, 1, EmptyAuthenticationConfig)

    fun enableSSL(enableSSL: Boolean) = copy(enableSSL = enableSSL)
    fun minimumServerProtocolVersion(minimumServerProtocolVersion: Int) = copy(minimumServerProtocolVersion = minimumServerProtocolVersion)
    fun username(username: String) = copy(
        authenticationConfig = if (authenticationConfig is BasicAuthenticationConfig)
            authenticationConfig.copy(username = username) else BasicAuthenticationConfig(username)
    )

    fun password(password: String) = copy(
        authenticationConfig = if (authenticationConfig is BasicAuthenticationConfig)
            authenticationConfig.copy(password = password) else BasicAuthenticationConfig(password = password)
    )

    fun bearerToken(tokenProvider: BearerTokenProvider) = copy(authenticationConfig = BearerTokenAuthenticationConfig(tokenProvider))
}
