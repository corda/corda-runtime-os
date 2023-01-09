package net.corda.p2p.linkmanager.utilities

import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants
import net.corda.data.p2p.NetworkType
import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.crypto.protocol.api.KeyAlgorithm
import net.corda.p2p.linkmanager.grouppolicy.GroupPolicyListener
import net.corda.p2p.linkmanager.grouppolicy.LinkManagerGroupPolicyProvider
import net.corda.p2p.linkmanager.membership.LinkManagerMembershipGroupReader
import net.corda.virtualnode.HoldingIdentity
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.mockito.kotlin.mock
import java.security.KeyPairGenerator
import java.security.MessageDigest

fun mockMembersAndGroups(
    vararg members: HoldingIdentity
): Pair<LinkManagerMembershipGroupReader, LinkManagerGroupPolicyProvider> {
    return mockMembers(members.toList()) to mockGroups(members.toList())
}
fun mockMembers(members: Collection<HoldingIdentity>): LinkManagerMembershipGroupReader {
    val endpoint = "http://10.0.0.1/"

    val provider = BouncyCastleProvider()
    val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
    val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, provider)
    val identities = members.associateWith {
        val keyPair = keyPairGenerator.generateKeyPair()
        LinkManagerMembershipGroupReader.MemberInfo(
            it,
            keyPair.public,
            KeyAlgorithm.ECDSA,
            endpoint,
        )
    }
    fun MessageDigest.hash(data: ByteArray): ByteArray {
        this.reset()
        this.update(data)
        return digest()
    }
    val hashToInfo = identities.values.associateBy {
        val publicKeyHash = messageDigest.hash(it.sessionPublicKey.encoded)
        (publicKeyHash to it.holdingIdentity)
    }
    return object : LinkManagerMembershipGroupReader {
        override fun getMemberInfo(requestingIdentity: HoldingIdentity, lookupIdentity: HoldingIdentity):
                LinkManagerMembershipGroupReader.MemberInfo? = identities[lookupIdentity]

        override fun getMemberInfo(requestingIdentity: HoldingIdentity, publicKeyHashToLookup: ByteArray)
            = hashToInfo[publicKeyHashToLookup to requestingIdentity]

        override val dominoTile = mock<DominoTile>()
    }
}

fun mockGroups(holdingIdentities: Collection<HoldingIdentity>): LinkManagerGroupPolicyProvider {
    return object : LinkManagerGroupPolicyProvider {
        override fun getGroupInfo(holdingIdentity: HoldingIdentity): GroupPolicyListener.GroupInfo? {
            return if (holdingIdentities.contains(holdingIdentity)) {
                GroupPolicyListener.GroupInfo(
                    holdingIdentity,
                    NetworkType.CORDA_5,
                    setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION),
                    emptyList(),
                    GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.NO_PKI,
                    null
                )
            } else {
                null
            }
        }

        override fun registerListener(groupPolicyListener: GroupPolicyListener) {
        }
        override val dominoTile = mock<DominoTile>()
    }
}
