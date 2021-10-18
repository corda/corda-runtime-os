package net.corda.membership

/**
 * Creates an instance of [MembershipGroupServiceFactory].
 */
interface MembershipGroupServiceFactoryProvider {
    /**
     * Unique provider name
     */
    val name: String

    fun create(): MembershipGroupServiceFactory
}


