package net.corda.httprpc.server.config

interface AzureAdSettingsProvider {
    companion object {
        const val AUTHORITY_FORMAT = "https://login.microsoftonline.com/%s/"
        const val AUTHORIZE_URL = "oauth2/v2.0/authorize"
        const val TOKEN_URL = "oauth2/v2.0/token"
    }

    fun getAuthority(): String {
        return AUTHORITY_FORMAT.format(getTenantId())
    }

    fun getAuthorizeUrl(): String {
        return getAuthority() + AUTHORIZE_URL
    }

    fun getTokenUrl(): String {
        return getAuthority() + TOKEN_URL
    }

    fun getTenantId(): String

    fun getClientId(): String

    fun getPrincipalClaimList(): List<String>

    fun getAppIdUri(): String?

    fun getClientSecret(): String?

    fun getTrustedIssuers(): List<String>?
}
