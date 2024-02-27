package net.corda.crypto.softhsm

fun interface WrappingRepositoryFactory {
    /**
     * Create a [WrappingRepository]
     *
     * Caller must call close.
     *
     * @param tenantId The tenant for the wrapping repository.
     * @result The new [WrappingRepository] instance.
     */
    fun create(tenantId: String): WrappingRepository
}
