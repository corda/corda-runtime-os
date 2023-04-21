package net.corda.securitymanager

/** Thrown for exceptions related to the Corda security managers. */
class SecurityManagerException(message: String, cause: Throwable? = null) : SecurityException(message, cause)