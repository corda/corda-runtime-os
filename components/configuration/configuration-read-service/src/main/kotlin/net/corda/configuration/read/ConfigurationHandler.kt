package net.corda.configuration.read

import net.corda.libs.configuration.SmartConfig

/**
 * The user configuration handler.
 *
 * Services requiring configuration should implement this interface to respond to configuration changes. On registering
 * with the configuration read service, the [onNewConfiguration] method will be called with all changed keys to inform
 * the registering service of the current configuration. On any subsequent change, the service is called again with the
 * changed keys and the updated configuration.
 *
 * The changed keys represent the top level configuration blocks that have changed in this configuration update.
 * Implementations may filter out configuration updates based on this.
 */
fun interface ConfigurationHandler {

    /**
     * Respond to a configuration change.
     *
     * @param changedKeys The set of top-level configuration keys that have changed in this configuration update. In
     *                    cases where the full configuration is being delivered, all known keys are present.
     * @param config The current full configuration.
     */
    fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, SmartConfig>)
}