package net.corda.p2p.linkmanager

import net.corda.data.identity.HoldingIdentity
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.linkmanager.PublicKeyReader.Companion.toKeyAlgorithm
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.parseList
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.membership.EndpointInfo
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda

class ForwardingMembershipGroupReader(
    private val groupReaderProvider: MembershipGroupReaderProvider,
    val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
): LinkManagerMembershipGroupReader {

    companion object {
        val logger = contextLogger()
    }

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

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = setOf(
            LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()
        ),
        managedChildren = setOf(
            NamedLifecycle(groupReaderProvider, LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>()),
        )
    )

    private fun MemberInfo.toLinkManagerMemberInfo(holdingIdentity: HoldingIdentity): LinkManagerMembershipGroupReader.MemberInfo? {
        val endpoint = getEndpoint(this.memberProvidedContext)
        return if (endpoint == null) {
            logger.warn("No valid endpoint with protocol version ${ProtocolConstants.PROTOCOL_VERSION} for member " +
                    "with identity $holdingIdentity.")
            null
        } else {
            LinkManagerMembershipGroupReader.MemberInfo(
                holdingIdentity,
                this.sessionInitiationKey,
                this.sessionInitiationKey.toKeyAlgorithm(),
                endpoint
            )
        }
    }

    private fun getEndpoint(memberContext: MemberContext): EndPoint? {
        val endpoints: List<EndpointInfo> = memberContext.parseList("corda.endpoints")
        return endpoints.singleOrNull { it.protocolVersion == ProtocolConstants.PROTOCOL_VERSION }?.url
    }
}