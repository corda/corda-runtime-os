package net.corda.libs.configuration

import com.typesafe.config.ConfigMergeable
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigOrigin
import com.typesafe.config.ConfigValue

interface SmartConfigObject: ConfigObject {
    /**
     * Returns an instance of [SmartConfigObject] that never reveals secrets.
     */
    fun toSafeConfig(): SmartConfigObject

    override fun toConfig(): SmartConfig

    override fun withOnlyKey(key: String?): SmartConfigObject

    override fun withoutKey(key: String?): SmartConfigObject

    override fun withValue(key: String?, value: ConfigValue?): SmartConfigObject

    override fun atPath(path: String?): SmartConfig

    override fun atKey(key: String?): SmartConfig

    override fun withFallback(other: ConfigMergeable?): SmartConfigObject

    override fun withOrigin(origin: ConfigOrigin?): SmartConfigObject

    override fun put(key: String, value: ConfigValue): SmartConfigValue?

    override fun remove(key: String?): SmartConfigValue?

    override fun get(key: String?): SmartConfigValue?
}

