package net.corda.rest.client.config

import net.corda.rest.client.auth.credentials.BearerTokenCredentialsProvider
import net.corda.rest.client.auth.credentials.BearerTokenProvider
import net.corda.rest.client.auth.credentials.CredentialsProvider
import net.corda.rest.client.auth.scheme.AuthenticationScheme
import net.corda.rest.client.auth.scheme.BearerTokenAuthenticationScheme

internal data class BearerTokenAuthenticationConfig(val tokenProvider: BearerTokenProvider) : AuthenticationConfig {
    private val provider by lazy {
        BearerTokenCredentialsProvider(tokenProvider)
    }

    override fun createScheme(): AuthenticationScheme {
        return BearerTokenAuthenticationScheme()
    }

    override fun getCredentialsProvider(): CredentialsProvider {
        return provider
    }
}
