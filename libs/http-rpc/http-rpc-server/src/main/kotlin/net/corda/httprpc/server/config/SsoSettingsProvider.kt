package net.corda.httprpc.server.config

interface SsoSettingsProvider {
    fun azureAd(): AzureAdSettingsProvider?
}
