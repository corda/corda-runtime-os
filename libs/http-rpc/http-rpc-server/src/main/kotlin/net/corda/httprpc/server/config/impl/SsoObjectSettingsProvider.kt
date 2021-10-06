package net.corda.httprpc.server.config.impl

import net.corda.httprpc.server.config.AzureAdSettingsProvider
import net.corda.httprpc.server.config.SsoSettingsProvider
import net.corda.httprpc.server.config.models.AzureAdSettings
import net.corda.httprpc.server.config.models.SsoSettings

internal class SsoObjectSettingsProvider(private val settings: SsoSettings) : SsoSettingsProvider {
    private class AzureAdObjectSettingsProvider(private val settings: AzureAdSettings) : AzureAdSettingsProvider {
        override fun getClientId(): String {
            return settings.clientId
        }

        override fun getClientSecret(): String? {
            return settings.clientSecret
        }

        override fun getTrustedIssuers(): List<String>? {
            return settings.trustedIssuers
        }

        override fun getTenantId(): String {
            return settings.tenantId
        }

        override fun getPrincipalClaimList(): List<String> {
            return settings.principalNameClaims
        }

        override fun getAppIdUri(): String? {
            return settings.appIdUri
        }
    }

    private val azureAd by lazy {
        if (settings.azureAd == null) null
        else AzureAdObjectSettingsProvider(settings.azureAd)
    }

    override fun azureAd(): AzureAdSettingsProvider? {
        return azureAd
    }
}
