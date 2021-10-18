package net.corda.membership

/**
 * Provides read-only access to instances of membership group service factory.
 */
interface MembershipGroupFactory {
    /**
     * Returns a singleton instance of [MembershipGroupServiceFactory]
     */
    fun getMembershipGroupServiceFactory(): MembershipGroupServiceFactory
}


