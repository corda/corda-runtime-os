package net.corda.libs.statemanager.api

import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.StateManagerConfig

/**
 * Factory for creating instances of a [StateManager] for a specific state type.
 */
interface StateManagerFactory {

    /**
     * Create a state manager from the given [config] for the given state type.
     *
     * @param config containing the state manager to connect to underlying storage mechanism.
     * @param stateType the type of state to be configured.
     * @return a state manager created from the given [config].
     */
    fun create(config: SmartConfig, stateType: StateManagerConfig.StateType): StateManager
}
