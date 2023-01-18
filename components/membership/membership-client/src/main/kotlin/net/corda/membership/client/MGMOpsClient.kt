package net.corda.membership.client

import net.corda.lifecycle.Lifecycle
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.ShortHash
import kotlin.jvm.Throws

/**
 * The MGM ops client to perform group operations.
 */
interface MGMOpsClient : Lifecycle {

    /**
     * Generates the Group Policy file to be used to register a new member to the MGM
     *
     * @param holdingIdentityShortHash The ID of the holding identity to be checked.
     * @return [String] Generated Group Policy Response.
     * @throws [CouldNotFindMemberException] If there is no member with [holdingIdentityShortHash].
     * @throws [MemberNotAnMgmException] If the member [holdingIdentityShortHash] is not an MGM.
     */
    @Throws(CouldNotFindMemberException::class, MemberNotAnMgmException::class)
    fun generateGroupPolicy(holdingIdentityShortHash: ShortHash): String

    fun mutualTlsAllowClientCertificate(
        holdingIdentityShortHash: ShortHash,
        subject: MemberX500Name,
    )
    fun mutualTlsDisallowClientCertificate(
        holdingIdentityShortHash: ShortHash,
        subject: MemberX500Name,
    )
    fun mutualTlsListClientCertificate(
        holdingIdentityShortHash: ShortHash,
    ): Collection<MemberX500Name>

}