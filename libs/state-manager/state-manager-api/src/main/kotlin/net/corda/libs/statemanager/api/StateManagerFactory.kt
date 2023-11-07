package net.corda.libs.statemanager.api

import net.corda.libs.configuration.SmartConfig

/**
 * Factory for creating type
 */
interface StateManagerFactory {

    /**
     * Create a state manager from the given [config].
     *
     * @param config containing the state manager to connect to underlying storage mechanism.
     * @return a state manager created from the given [config].
     */
    fun create(config: SmartConfig): StateManager
}
