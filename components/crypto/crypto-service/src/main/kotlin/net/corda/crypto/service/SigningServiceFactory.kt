package net.corda.crypto.service

import net.corda.lifecycle.Lifecycle

/**
 * The [SigningServiceFactory] provides operations to create service side services.
 */
interface SigningServiceFactory : Lifecycle {
    /**
     * Returns s service instance of the [SigningService].
     */
    fun getInstance(): SigningService
}