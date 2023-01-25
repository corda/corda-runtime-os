package net.corda.httprpc.client.config

import net.corda.httprpc.client.auth.credentials.CredentialsProvider
import net.corda.httprpc.client.auth.credentials.EmptyCredentialsProvider
import net.corda.httprpc.client.auth.scheme.AuthenticationScheme
import net.corda.httprpc.client.auth.scheme.NoopAuthenticationScheme

internal object EmptyAuthenticationConfig : AuthenticationConfig {
    override fun createScheme(): AuthenticationScheme {
        return NoopAuthenticationScheme
    }

    override fun getCredentialsProvider(): CredentialsProvider {
        return EmptyCredentialsProvider
    }
}
