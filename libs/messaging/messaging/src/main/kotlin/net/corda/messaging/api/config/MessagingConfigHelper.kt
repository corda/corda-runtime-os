package net.corda.messaging.api.config

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.exception.CordaMessageAPIConfigException
import net.corda.schema.configuration.ConfigKeys.Companion.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.MESSAGING_CONFIG

/**
 * Utility for generating a valid messaging configuration given a configuration map from the configuration read service.
 *
 * The library requires configuration from under both the boot and messaging keys.
 */
fun Map<String, SmartConfig>.toMessagingConfig() : SmartConfig {
    val bootConfig = this[BOOT_CONFIG] ?: throw CordaMessageAPIConfigException(BOOT_CONFIG)
    val messagingConfig = this[MESSAGING_CONFIG] ?: throw CordaMessageAPIConfigException(MESSAGING_CONFIG)
    return messagingConfig.withFallback(bootConfig)
}