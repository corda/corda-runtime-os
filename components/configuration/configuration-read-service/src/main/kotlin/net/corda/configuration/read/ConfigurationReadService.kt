package net.corda.configuration.read

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.Resource

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
     * Note that the provided callback is invoked in the context of the configuration read service's lifecycle event
     * processing thread. This matters if component state is updated in this callback, as there is then a race condition
     * between the callback executing and any component lifecycle events. This can be mitigated by posting a lifecycle
     * event to the component on configuration change. See [registerComponentForUpdates] for an alternative registration
     * mechanism that automates this.
     *
     * Invoking this before the service is started will result in the registration being lost, so ensure that the service
     * is started before calling this API. For most components, this can be assumed as the processor should start the
     * config read service very early in its lifecycle.
     *
     * @param configHandler The user configuration handler. See [ConfigurationHandler].
     * @return A handle for this registration, which may be closed to unregister from the configuration read service.
     */
    fun registerForUpdates(configHandler: ConfigurationHandler): Resource

    /**
     * Register a component for configuration updates on a particular set of top-level keys.
     *
     * This function provides a standard mechanism for components with lifecycle to receive configuration events. The
     * configuration read service will deliver a [ConfigChangedEvent] when a configuration update occurs, provided that
     * the update is for one of the [requiredKeys] and all the [requiredKeys] have been received.
     *
     * The registration posts the [ConfigChangedEvent] to the provided coordinator whenever the relevant conditions are
     * met. By doing this, the threading problems described in the [registerForUpdates] function are avoided.
     *
     * @param coordinator The lifecycle coordinator of the registering component. The [ConfigChangedEvent] will be
     *                    delivered to this coordinator.
     * @param requiredKeys The set of top-level configuration keys the component requires to configure itself. Once all
     *                     these are present in the configuration, configuration updates will be delivered.
     * @return A handle for this registration, which may be closed to unregister from the configuration read service.
     */
    fun registerComponentForUpdates(coordinator: LifecycleCoordinator, requiredKeys: Set<String>): Resource

    /**
     * Provide bootstrap configuration to the configuration read service.
     *
     * This should be called by the application, providing enough initial configuration to connect to the message bus
     * and retrieve the full configuration. Other services will not need to call this.
     *
     * Calling this multiple times with different bootstrap configurations will result in an error later on, when
     * the BootstrapConfigProvided event is processed. However, calling this multiple times with identical
     * bootstrap configurations is harmless (and is simply logged at debug level).
     * An error is also thrown later if the configuration does not allow the configuration read service to
     * access the full configuration.
     *
     * @param config The bootstrap configuration to connect to the message bus.
     * @throws ConfigurationException If the bootstrap configuration is provided after a connection has been
     */
    fun bootstrapConfig(config: SmartConfig)
}

