package net.corda.membership.read

/**
 * Provides read-only access to membership data via instances of membership group service factory.
 */
interface MembershipGroupFactory {
    /**
     * Returns a singleton instance of [MembershipGroupServiceFactory]
     */
    fun getMembershipGroupServiceFactory(): MembershipGroupServiceFactory
}
