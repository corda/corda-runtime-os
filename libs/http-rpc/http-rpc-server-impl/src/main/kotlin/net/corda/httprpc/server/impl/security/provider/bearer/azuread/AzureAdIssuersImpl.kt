package net.corda.httprpc.server.impl.security.provider.bearer.azuread

import net.corda.httprpc.server.config.AzureAdSettingsProvider

internal class AzureAdIssuersImpl(private val settings: AzureAdSettingsProvider) : AzureAdIssuers {
    companion object {
        private const val LOGIN_MICROSOFT_ONLINE_ISSUER = "https://login.microsoftonline.com/"
        private const val STS_WINDOWS_ISSUER = "https://sts.windows.net/"
        private const val STS_CHINA_CLOUD_API_ISSUER = "https://sts.chinacloudapi.cn/"
        private const val PATH = "/"
        private const val PATH_V2 = "/v2.0"
    }

    private val issuers: MutableSet<String>

    init {
        val issuerBaseList = setOf(LOGIN_MICROSOFT_ONLINE_ISSUER, STS_WINDOWS_ISSUER, STS_CHINA_CLOUD_API_ISSUER)
        issuers = issuerBaseList
            .map { root -> root + settings.getTenantId() + PATH }
            .toSet()
            .plus(issuerBaseList
                .map { root -> root + settings.getTenantId() + PATH_V2 })
            .toMutableSet()
    }

    override fun valid(issuer: String?): Boolean {
        return issuers.contains(issuer)
    }

    override fun addTrustedIssuer(issuer: String) {
        issuers.add(issuer)
    }
}
