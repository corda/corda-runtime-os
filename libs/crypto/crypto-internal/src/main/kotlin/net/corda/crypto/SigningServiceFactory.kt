package net.corda.crypto

/**
 * The factory to get or create instances of [SigningService].
 */
interface SigningServiceFactory {

    /**
     * Returns an instance of the [SigningService] for the specified tenant
     */
    fun getSigningService(
        tenantId: String
    ): SigningService
}