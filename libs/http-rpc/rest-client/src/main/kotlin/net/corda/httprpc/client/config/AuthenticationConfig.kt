package net.corda.httprpc.client.config

import net.corda.httprpc.client.auth.credentials.CredentialsProvider
import net.corda.httprpc.client.auth.scheme.AuthenticationScheme

/**
 * Provides authentication information for REST calls.
 */
interface AuthenticationConfig {
    /**
     * @return an AuthenticationScheme instance
     */
    fun createScheme(): AuthenticationScheme

    /**
     * @return Credentials used for authentication
     */
    fun getCredentialsProvider(): CredentialsProvider
}
