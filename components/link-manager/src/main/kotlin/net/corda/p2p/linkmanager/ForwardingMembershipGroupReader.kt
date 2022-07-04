package net.corda.p2p.linkmanager

import net.corda.data.identity.HoldingIdentity
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.linkmanager.PublicKeyReader.Companion.toKeyAlgorithm
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.parseList
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.membership.EndpointInfo
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.toCorda

class ForwardingMembershipGroupReader(private val groupReaderProvider: MembershipGroupReaderProvider): LinkManagerMembershipGroupReader {

    override fun getMemberInfo(requestingIdentity: HoldingIdentity, lookupIdentity: HoldingIdentity):
            LinkManagerMembershipGroupReader.MemberInfo? {
        return groupReaderProvider.getGroupReader(requestingIdentity.toCorda())
            .lookup(MemberX500Name.parse(lookupIdentity.x500Name))
            ?.toLinkManagerMemberInfo(lookupIdentity)
    }

    override fun getMemberInfo(requestingIdentity: HoldingIdentity, publicKeyHashToLookup: ByteArray):
            LinkManagerMembershipGroupReader.MemberInfo? {
        val lookup = groupReaderProvider
            .getGroupReader(requestingIdentity.toCorda())
            .lookup(PublicKeyHash.Companion.parse(publicKeyHashToLookup))
        return lookup?.toLinkManagerMemberInfo(HoldingIdentity(lookup.name.toString(), requestingIdentity.groupId))
    }

    override val dominoTile: DominoTile
        get() = TODO("Not yet implemented")

    private fun MemberInfo.toLinkManagerMemberInfo(holdingIdentity: HoldingIdentity): LinkManagerMembershipGroupReader.MemberInfo {
        return LinkManagerMembershipGroupReader.MemberInfo(
            holdingIdentity,
            this.sessionInitiationKey,
            this.sessionInitiationKey.toKeyAlgorithm(),
            getEndpoint(this.memberProvidedContext)
        )
    }

    private fun getEndpoint(memberContext: MemberContext): EndPoint {
        val endpoints: List<EndpointInfo> = memberContext.parseList("corda.endpoints")
        return endpoints.singleOrNull { it.protocolVersion == ProtocolConstants.PROTOCOL_VERSION }?.url
            ?: throw NoValidEndpointException(ProtocolConstants.PROTOCOL_VERSION)
    }

    class NoValidEndpointException(val requiredProtocolVersion: Int): IllegalArgumentException("")

}