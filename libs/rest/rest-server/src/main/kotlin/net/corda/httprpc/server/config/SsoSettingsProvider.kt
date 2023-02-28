package net.corda.rest.server.config

interface SsoSettingsProvider {
    fun azureAd(): AzureAdSettingsProvider?
}
