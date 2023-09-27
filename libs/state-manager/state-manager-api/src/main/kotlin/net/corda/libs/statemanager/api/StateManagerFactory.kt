package net.corda.libs.statemanager.api

import net.corda.libs.configuration.SmartConfig

/**
 * Factory for creating type
 */
interface StateManagerFactory {

    /**
     * Create a state manager from the given [messagingConfig].
     *
     * @param messagingConfig containing the state manager to connect to underlying storage mechanism.
     * @return a state manager created from the given [messagingConfig].
     */
    fun create(messagingConfig: SmartConfig): StateManager
}
