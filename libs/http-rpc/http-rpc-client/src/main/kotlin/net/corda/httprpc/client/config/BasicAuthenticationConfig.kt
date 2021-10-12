package net.corda.httprpc.client.config

import net.corda.httprpc.client.auth.credentials.BasicAuthCredentials
import net.corda.httprpc.client.auth.credentials.CredentialsProvider
import net.corda.httprpc.client.auth.scheme.AuthenticationScheme
import net.corda.httprpc.client.auth.scheme.BasicAuthenticationScheme

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
