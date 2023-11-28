package net.corda.libs.external.messaging

interface ExternalMessagingConfigProvider {
    fun getDefaults(): ExternalMessagingConfigDefaults
}
