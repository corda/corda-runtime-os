package net.corda.libs.configuration

import com.typesafe.config.ConfigMergeable
import com.typesafe.config.ConfigOrigin
import com.typesafe.config.ConfigValue

interface SmartConfigValue : ConfigValue {
    /**
     * Returns an instance of [SmartConfigValue] that never reveals secrets.
     */
    fun toSafeConfigValue(): SmartConfigValue

    override fun withFallback(other: ConfigMergeable?): ConfigValue?

    override fun atPath(path: String?): SmartConfig

    override fun atKey(key: String?): SmartConfig

    override fun withOrigin(origin: ConfigOrigin?): SmartConfigValue?
}