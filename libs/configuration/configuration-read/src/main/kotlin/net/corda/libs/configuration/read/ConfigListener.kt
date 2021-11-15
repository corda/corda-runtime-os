package net.corda.libs.configuration.read

import net.corda.libs.configuration.SmartConfig

fun interface ConfigListener {
    /**
     * The implementation of this functional class will be used to notify you of any configuration changes including
     * when you first register.
     * @param changedKeys a set of keys that have changed since last update
     * @param currentConfigurationSnapshot the snapshot of all currently available configuration objects
     */
    fun onUpdate(changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, SmartConfig>)
}
