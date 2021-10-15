package net.corda.securitymanager

/** A service for starting and stopping a Corda security manager. */
interface SecurityManagerService {
    /**
     * Starts either the discovery or the restrictive security manager, based on the `isDiscoveryMode` flag.
     *
     * Replaces the existing Corda security manager, if one is already installed.
     */
    fun start(isDiscoveryMode: Boolean = false)
}