package net.corda.membership

/**
 * Creates an instance of [MembershipGroupStorageServiceFactory].
 */
interface MembershipGroupStorageServiceFactoryProvider {
    /**
     * Unique provider name
     */
    val name: String

    fun create(): MembershipGroupStorageServiceFactory
}
