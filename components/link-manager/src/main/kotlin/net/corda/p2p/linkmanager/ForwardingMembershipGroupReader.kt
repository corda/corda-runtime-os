package net.corda.p2p.linkmanager

import net.corda.data.identity.HoldingIdentity
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.membership.lib.MemberInfoExtension.Companion.endpoints
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.linkmanager.PublicKeyReader.Companion.toKeyAlgorithm
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.toCorda

internal class ForwardingMembershipGroupReader(
    private val groupReaderProvider: MembershipGroupReaderProvider,
    val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
): LinkManagerMembershipGroupReader {

    companion object {
        private val logger = contextLogger()
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
            .lookupBySessionKey(PublicKeyHash.Companion.parse(publicKeyHashToLookup))
        return lookup?.toLinkManagerMemberInfo(HoldingIdentity(lookup.name.toString(), requestingIdentity.groupId))
    }

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = setOf(LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>()),
        managedChildren = setOf(
            NamedLifecycle(groupReaderProvider, LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>()),
        )
    )

    private fun MemberInfo.toLinkManagerMemberInfo(holdingIdentity: HoldingIdentity): LinkManagerMembershipGroupReader.MemberInfo? {
        val endpoint = this.endpoints.firstOrNull { it.protocolVersion == ProtocolConstants.PROTOCOL_VERSION }?.url
        return if (endpoint == null) {
            logger.warn("No valid endpoint with protocol version ${ProtocolConstants.PROTOCOL_VERSION} for member with identity" +
                " $holdingIdentity. Endpoints available: ${this.endpoints.map { "${it.url} (version: ${it.protocolVersion})" }}.")
            null
        } else {
            LinkManagerMembershipGroupReader.MemberInfo(
                HoldingIdentity(this.name.toString(), this.groupId),
                this.sessionInitiationKey,
                this.sessionInitiationKey.toKeyAlgorithm(),
                endpoint
            )
        }
    }
}