package net.corda.p2p.linkmanager.utilities

import net.corda.data.identity.HoldingIdentity
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.p2p.NetworkType
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.crypto.protocol.api.KeyAlgorithm
import net.corda.p2p.linkmanager.GroupPolicyListener
import net.corda.p2p.linkmanager.LinkManagerGroupPolicyProvider
import net.corda.p2p.linkmanager.LinkManagerMembershipGroupReader
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.mockito.kotlin.mock
import java.security.KeyPairGenerator
import java.security.MessageDigest

fun mockMembersAndGroups(
    vararg members: HoldingIdentity
): Pair<LinkManagerMembershipGroupReader, LinkManagerGroupPolicyProvider> {
    return mockMembers(members.toList()) to mockGroups(members.map { it.groupId })
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
        (publicKeyHash to it.holdingIdentity.groupId)
    }
    return object : LinkManagerMembershipGroupReader {
        override fun getMemberInfo(holdingIdentity: HoldingIdentity) = identities[holdingIdentity]

        override fun getMemberInfo(hash: ByteArray, groupId: String) = hashToInfo[hash to groupId]

        override val dominoTile = mock<DominoTile>()
    }
}

fun mockGroups(groups: Collection<String>): LinkManagerGroupPolicyProvider {
    val groupSet = groups.toSet()
    return object : LinkManagerGroupPolicyProvider {
        override fun getGroupInfo(groupId: String): GroupPolicyListener.GroupInfo? {
            return if (groupSet.contains(groupId)) {
                GroupPolicyListener.GroupInfo(
                    groupId,
                    NetworkType.CORDA_5,
                    setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION),
                    emptyList()
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
