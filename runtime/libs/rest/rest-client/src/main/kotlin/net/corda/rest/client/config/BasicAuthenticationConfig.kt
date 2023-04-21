package net.corda.rest.client.config

import net.corda.rest.client.auth.credentials.BasicAuthCredentials
import net.corda.rest.client.auth.credentials.CredentialsProvider
import net.corda.rest.client.auth.scheme.AuthenticationScheme
import net.corda.rest.client.auth.scheme.BasicAuthenticationScheme

internal data class BasicAuthenticationConfig(val username: String = "", val password: String = "") : AuthenticationConfig {
    private val provider by lazy {
        BasicAuthCredentials(username, password)
    }

    override fun createScheme(): AuthenticationScheme {
        return BasicAuthenticationScheme()
    }

    override fun getCredentialsProvider(): CredentialsProvider {
        return provider
    }
}
