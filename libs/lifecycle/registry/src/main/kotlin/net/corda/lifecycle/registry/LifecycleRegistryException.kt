package net.corda.lifecycle.registry

/**
 * Exception thrown from the registry API.
 */
class LifecycleRegistryException(message: String, cause: Throwable? = null) : Exception(message, cause)
