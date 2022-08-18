package net.corda.membership.synchronisation

import net.corda.data.membership.command.synchronisation.SynchronisationCommand
import net.corda.lifecycle.Lifecycle
import net.corda.membership.lib.exceptions.SynchronisationProtocolSelectionException

/**
 * Proxy for data synchronisation: passes the sync commands for further processing to the appropriate [SynchronisationService].
 * Synchronisation requests will be handled by an mgm implementation and membership updates by a
 * member implementation
 * Implementations of this interface must coordinate lifecycles of all mgm and member implementations.
 */
interface SynchronisationProxy : Lifecycle {
    /**
     * Selects the appropriate [SynchronisationService] and leaves the further processing of that command to that service.
     *
     * @param command The sync command which needs to be processed.
     *
     * @throws [SynchronisationProtocolSelectionException] if the synchronisation protocol could not be selected.
     */
    fun handleCommand(command: SynchronisationCommand)
}
