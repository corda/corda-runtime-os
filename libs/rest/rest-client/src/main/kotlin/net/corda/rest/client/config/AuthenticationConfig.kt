package net.corda.rest.client.config

import net.corda.rest.client.auth.credentials.CredentialsProvider
import net.corda.rest.client.auth.scheme.AuthenticationScheme

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
