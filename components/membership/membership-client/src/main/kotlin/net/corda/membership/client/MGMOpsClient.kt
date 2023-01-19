package net.corda.membership.client

import net.corda.lifecycle.Lifecycle
import net.corda.membership.client.dto.PreAuthTokenDto
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.ShortHash
import java.util.UUID
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

    /**
     * Generate a preAuthToken.
     *
     * @param holdingIdentityShortHash The holding identity ID of the MGM.
     * @param ownerX500Name The X500 name of the member to preauthorize.
     * @param ttl A (time-to-live) unix timestamp (in milliseconds) after which this token will become invalid. Defaults to infinity.
     * @param remarks Some optional remarks.
     */
    fun generatePreAuthToken(
        holdingIdentityShortHash: ShortHash,
        ownerX500Name: MemberX500Name,
        ttl: Int,
        remarks: String?,
    ): PreAuthTokenDto

    /**
     * Query for preAuthTokens.
     *
     * @param holdingIdentityShortHash The holding identity ID of the MGM.
     * @param ownerX500Name The X500 name of the member to query for.
     * @param preAuthTokenId The token ID to query for.
     * @param viewInactive Return in tokens with status [PreAuthTokenStatus.REVOKED], [PreAuthTokenStatus.CONSUMED],
     * [PreAuthTokenStatus.AUTO_INVALIDATED] as well as [PreAuthTokenStatus.AVAILABLE].
     */
    fun getPreAuthTokens(
        holdingIdentityShortHash: ShortHash,
        ownerX500Name: MemberX500Name?,
        preAuthTokenId: UUID?,
        viewInactive: Boolean
    ): Collection<PreAuthTokenDto>

    /**
     * Revoke a preAuthToken.
     *
     * @param holdingIdentityShortHash The holding identity ID of the MGM.
     * @param preAuthTokenId The token ID to revoke.
     * @param remarks Some optional remarks about why the token was revoked.
     */
    fun revokePreAuthToken(
        holdingIdentityShortHash: ShortHash,
        preAuthTokenId: UUID,
        remarks: String?
    ): PreAuthTokenDto

}