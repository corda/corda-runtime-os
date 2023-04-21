package net.corda.rest.server.impl.security.provider.scheme

/**
 * Provides data to RestServerInternal to generate WWW-Authenticate headers on auth failures.
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/WWW-Authenticate
 */
interface AuthenticationSchemeProvider {
    companion object {
        const val REALM_KEY = "realm"
    }

    /**
     * Name of the scheme
     */
    val authenticationMethod: AuthenticationScheme

    /**
     * Additional attributes for this scheme like 'realm'
     */
    fun provideParameters(): Map<String, String>
}
