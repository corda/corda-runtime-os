package net.corda.rest.client.config

import net.corda.rest.client.auth.credentials.CredentialsProvider
import net.corda.rest.client.auth.credentials.EmptyCredentialsProvider
import net.corda.rest.client.auth.scheme.AuthenticationScheme
import net.corda.rest.client.auth.scheme.NoopAuthenticationScheme

internal object EmptyAuthenticationConfig : AuthenticationConfig {
    override fun createScheme(): AuthenticationScheme {
        return NoopAuthenticationScheme
    }

    override fun getCredentialsProvider(): CredentialsProvider {
        return EmptyCredentialsProvider
    }
}
