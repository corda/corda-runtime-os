package net.corda.libs.configuration

import com.typesafe.config.Config

/**
 * net.corda.libs.configuration.SmartConfig extends [Config] with additional metadata to support things like
 * Secrets.
 */
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
}

