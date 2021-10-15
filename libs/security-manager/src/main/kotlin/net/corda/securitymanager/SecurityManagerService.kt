package net.corda.securitymanager

/** A service for starting and interacting with a Corda security manager. */
interface SecurityManagerService {
    /**
     * Starts either the discovery or the restrictive security manager, based on the `isDiscoveryMode` flag.
     *
     * Throws `SecurityManagerException` if a Corda security manager has already been started.
     */
    fun start(isDiscoveryMode: Boolean = false)
}