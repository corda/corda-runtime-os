package net.corda.flow.maintenance

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager

/**
 * Factory for generating flow maintenance event handlers.
 */
interface FlowMaintenanceHandlersFactory {

    /**
     * Create a handler for scheduled task triggers handling session timeout.
     *
     * @param stateManager The state manager the handler should use to retrieve states.
     * @return A session timeout task processor.
     */
    fun createScheduledTaskHandler(stateManager: StateManager): SessionTimeoutTaskProcessor

    /**
     * Create a handler for session timeout events.
     *
     * @param stateManager The state manager the handler should use to retrieve states.
     * @param config The flow configuration.
     * @return A timeout event handler.
     */
    fun createTimeoutEventHandler(stateManager: StateManager, config: SmartConfig): TimeoutEventCleanupProcessor
}