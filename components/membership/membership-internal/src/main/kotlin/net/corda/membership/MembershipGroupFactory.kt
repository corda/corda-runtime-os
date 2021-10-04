package net.corda.membership

/**
 * Provides access to instances of memebrship services.
 */
interface MembershipGroupFactory {
    /**
     * Returns a singleton instance of [MembershipGroupLookupService]
     */
    fun getLookupService(): MembershipGroupInfoLookupService
}

