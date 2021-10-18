package net.corda.membership.write

/**
 * Provides API to update stored member data via instances of membership group storage service factory.
 */
interface MembershipGroupStorageFactory {
    /**
     * Returns a singleton instance of [MembershipGroupStorageServiceFactory].
     */
    fun getMembershipGroupStorageServiceFactory(): MembershipGroupStorageServiceFactory
}
