package net.corda.httprpc.server.impl.security.provider.scheme

/**
 * Standard schemes for the WWW-Authenticate header
 * https://www.iana.org/assignments/http-authschemes/http-authschemes.xhtml
 */
enum class AuthenticationScheme(val scheme: String) {
    BASIC("Basic"),
    BEARER("Bearer");

    override fun toString(): String {
        return scheme
    }
}
