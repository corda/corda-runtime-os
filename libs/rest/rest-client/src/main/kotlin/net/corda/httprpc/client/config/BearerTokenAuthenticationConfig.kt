package net.corda.httprpc.client.config

import net.corda.httprpc.client.auth.credentials.BearerTokenCredentialsProvider
import net.corda.httprpc.client.auth.credentials.BearerTokenProvider
import net.corda.httprpc.client.auth.credentials.CredentialsProvider
import net.corda.httprpc.client.auth.scheme.AuthenticationScheme
import net.corda.httprpc.client.auth.scheme.BearerTokenAuthenticationScheme

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
