package net.corda.libs.configuration

import com.typesafe.config.Config

interface SmartConfigFactory {
    /**
     * Convert a regular [Config] object into a [SmartConfig] one.
     * Depending on the implementation of this, this may return one that supports
     * resolving secrets.
     *
     * @param config
     * @return
     */
    fun create(config: Config): SmartConfig

    /**
     * Convert a regular [Config] object into a [SmartConfig] one
     * that never reveals secrets
     *
     * @param config
     * @return
     */
    fun createSafe(config: Config): SmartConfig
}