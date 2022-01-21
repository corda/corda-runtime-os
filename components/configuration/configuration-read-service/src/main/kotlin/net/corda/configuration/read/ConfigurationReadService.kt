package net.corda.configuration.read

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

/**
 * A service managing the configuration in the process.
 *
 * The [ConfigurationReadService] is responsible for reading configuration from the message bus and exposing this to
 * other services in the system. Configuration is expected to be dynamic, so services must be prepared to respond to
 * configuration changes.
 *
 * Note that calling start or stop on this service is idempotent, so it is safe for other services to call start to make
 * sure that the service is up.
 *
 * This service will transition to [LifecycleStatus.UP] once the first snapshot of configuration is received.
 */
interface ConfigurationReadService : Lifecycle {

    /**
     * Register for configuration updates.
     *
     * The provided handler will be invoked when the first batch of configuration becomes available, or on registration
     * if the configuration is already available. After this, the handler will be invoked every time the configuration
     * changes.
     *
     * The returned handle may be closed to unregister from the configuration read service.
     *
     * Note that this should not be called before the config read service is started. Doing so will result in the
     * registration not going through correctly.
     *
     * @param configHandler The user configuration handler. See [ConfigurationHandler].
     * @return A handle for this registration, which may be closed to unregister from the configuration read service.
     */
    fun registerForUpdates(configHandler: ConfigurationHandler) : AutoCloseable

    /**
     * Provide bootstrap configuration to the configuration service.
     *
     * This should be called by the application, providing enough initial configuration to connect to the message bus
     * and retrieve the full configuration. Other services will not need to call this.
     *
     * Calling this multiple times will result in an error. An error is also thrown if the configuration does not allow
     * the configuration read service to access the full configuration.
     *
     * @param config The bootstrap configuration to connect to the message bus.
     * @throws ConfigurationException If the bootstrap configuration is provided after a connection has been
     *                                established, or if the configuration does not allow access.
     */
    fun bootstrapConfig(config: SmartConfig)
}
