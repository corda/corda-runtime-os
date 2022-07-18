package net.corda.membership.httprpc.v1.types.response

import java.time.Instant

/**
 * Data class representing the latest known status of a member's registration.
 *
 * @param fileFormatVersion File Format Version of the group policy.
 * @param groupId Group Id fo MGM.
 * @param registrationProtocol Registration Protocol used by MGM.
 * @param synchronisationProtocol Synchronisation Protocol used by MGM.
 * @param protocolParameters Protocol Parameters used by MGM.
 * @param p2pParameters P2P Parameters used by MGM.
 * @param mgmInfo MGM Information.
 * @param cipherSuite Cipher Suite used by MGM.
 */
data class MGMGenerateGroupPolicyResponse(
    val fileFormatVersion: Int,
    val groupId: String,
    val registrationProtocol: String,
    val synchronisationProtocol: String,
    val protocolParameters: Map<String, String>,
    val p2pParameters: Map<String, String>,
    val mgmInfo: Map<String, String>,
    val cipherSuite: Map<String, String>
)