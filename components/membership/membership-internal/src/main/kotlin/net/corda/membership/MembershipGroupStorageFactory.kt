package net.corda.membership

/**
 * Provides read-only access to instances of membership group storage service factory.
 */
interface MembershipGroupStorageFactory {
    /**
     * Returns a singleton instance of [MembershipGroupStorageServiceFactory].
     */
    fun getMembershipGroupStorageServiceFactory(): MembershipGroupStorageServiceFactory
}
