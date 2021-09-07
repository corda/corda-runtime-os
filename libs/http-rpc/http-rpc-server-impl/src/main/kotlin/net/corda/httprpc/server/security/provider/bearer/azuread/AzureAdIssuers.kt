package net.corda.httprpc.server.security.provider.bearer.azuread

internal interface AzureAdIssuers {
    fun valid(issuer: String?): Boolean

    fun addTrustedIssuer(issuer: String)
}
