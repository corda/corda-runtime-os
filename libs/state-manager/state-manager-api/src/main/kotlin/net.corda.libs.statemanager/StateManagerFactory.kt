package net.corda.libs.statemanager

import net.corda.libs.configuration.SmartConfig

/**
 * Factory for creating type
 */
interface StateManagerFactory {
    /**
     * Create a state manager with the given config.
     *
     * @param config for the state manager to connect to underlying storage mechanism.
     * @return a state manager
     */
    fun create(config: SmartConfig): StateManager
}