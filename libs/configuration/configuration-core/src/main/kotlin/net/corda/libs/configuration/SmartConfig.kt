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
    companion object {
        const val SECRET_KEY = "configSecret"
    }

    /**
     * Factory for creating similar [SmartConfig] objects.
     */
    val factory: SmartConfigFactory

    /**
     * Returns true of the value for [path] is a secret
     *
     * @param path
     * @return
     */
    fun isSecret(path: String): Boolean

    /**
     * Convert a [Config] into a [SmartConfig] with the same extensions as [this]
     *
     * @param config
     * @return
     */
    fun convert(config: Config): SmartConfig

    /**
     * Returns an instance of [Config] that never reveals secrets.
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

fun Config.getStringOrDefault(path: String, default: String): String {
    if(this.hasPath(path)) return this.getString(path)
    return default
}


