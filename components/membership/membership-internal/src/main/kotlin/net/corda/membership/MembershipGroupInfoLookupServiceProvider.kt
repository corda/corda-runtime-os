package net.corda.membership

/**
 * Creates an instance of [MembershipGroupLookupService].
 */
interface MembershipGroupInfoLookupServiceProvider {
    /**
     * Unique provider name
     */
    val name: String

    fun create(): MembershipGroupInfoLookupService
}

