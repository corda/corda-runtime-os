package net.corda.securitymanager

/** A service for starting a Corda security manager. */
interface SecurityManagerService {
    /**
     * Starts the restrictive security manager.
     *
     * Replaces the existing Corda security manager, if one is already installed.
     */
    fun start()

    /**
     * Starts the discovery security manager with the provided [prefixes].
     *
     * Replaces the existing Corda security manager, if one is already installed.
     */
    fun startDiscoveryMode(prefixes: Collection<String>)
}