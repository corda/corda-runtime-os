package net.corda.libs.configuration

import com.typesafe.config.Config
import com.typesafe.config.ConfigMergeable
import com.typesafe.config.ConfigResolveOptions
import com.typesafe.config.ConfigValue

/**
 * net.corda.libs.configuration.SmartConfig extends [Config] with additional metadata to support things like
 * Secrets.
 */
@Suppress("TooManyFunctions")
interface SmartConfig : Config {

    /**
     * Returns true of the value for [path] is a secret
     *
     * @param path
     * @return
     */
    fun isSecret(path: String): Boolean

    /**
     * Returns an instance of [SmartConfig] that never reveals secrets.
     */
    fun toSafeConfig(): Config

    override fun withFallback(other: ConfigMergeable?): SmartConfig

    override fun root(): SmartConfigObject

    override fun resolve(): SmartConfig

    override fun resolve(options: ConfigResolveOptions?): SmartConfig

    override fun resolveWith(source: Config): SmartConfig

    override fun resolveWith(source: Config, options: ConfigResolveOptions?): SmartConfig

    override fun entrySet(): MutableSet<MutableMap.MutableEntry<String, SmartConfigValue>>

    override fun getObject(path: String?): SmartConfigObject

    override fun getConfig(path: String?): SmartConfig

    override fun getValue(path: String?): SmartConfigValue

    override fun withOnlyPath(path: String?): SmartConfig

    override fun withoutPath(path: String?): SmartConfig

    override fun atPath(path: String?): SmartConfig

    override fun atKey(key: String?): SmartConfig

    override fun withValue(path: String?, value: ConfigValue?): SmartConfig
}

const val SECRETS_INDICATOR = "isSmartConfigSecret"

