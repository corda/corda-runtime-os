package net.corda.libs.configuration

import com.typesafe.config.ConfigValue

interface SmartConfigValue : ConfigValue {
    /**
     * Returns an instance of [SmartConfigValue] that never reveals secrets.
     */
    fun toSafeConfig(): SmartConfigValue
}