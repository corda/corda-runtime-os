package net.corda.membership.synchronisation

import net.corda.lifecycle.Lifecycle

/**
 * Proxy for data synchronisation: passes the sync commands for further processing to the appropriate [SynchronisationService].
 * Synchronisation requests will be handled by an mgm implementation and membership updates by a
 * member implementation
 * Implementations of this interface must coordinate lifecycles of all mgm and member implementations.
 */
interface SynchronisationProxy : Lifecycle
