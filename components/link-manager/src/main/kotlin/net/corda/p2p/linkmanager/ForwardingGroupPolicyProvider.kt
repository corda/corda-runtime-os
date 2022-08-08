package net.corda.p2p.linkmanager

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.p2p.NetworkType
import net.corda.p2p.crypto.ProtocolMode
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService

internal class ForwardingGroupPolicyProvider(coordinatorFactory: LifecycleCoordinatorFactory,
                                             private val groupPolicyProvider: GroupPolicyProvider,
                                             virtualNodeInfoReadService: VirtualNodeInfoReadService,
                                             cpiInfoReadService: CpiInfoReadService,
                                             membershipQueryClient: MembershipQueryClient): LinkManagerGroupPolicyProvider {


    private val dependentChildren = setOf(
        LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
        LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
        LifecycleCoordinatorName.forComponent<CpiInfoReadService>(),
        LifecycleCoordinatorName.forComponent<MembershipQueryClient>()
    )

    private val managedChildren = setOf(
        NamedLifecycle(groupPolicyProvider, LifecycleCoordinatorName.forComponent<GroupPolicyProvider>()),
        NamedLifecycle(virtualNodeInfoReadService, LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()),
        NamedLifecycle(cpiInfoReadService, LifecycleCoordinatorName.forComponent<CpiInfoReadService>()),
        NamedLifecycle(membershipQueryClient, LifecycleCoordinatorName.forComponent<MembershipQueryClient>())
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        coordinatorFactory,
        dependentChildren = dependentChildren,
        managedChildren = managedChildren
    )

    override fun getGroupInfo(holdingIdentity: HoldingIdentity): GroupPolicyListener.GroupInfo? {
       return groupPolicyProvider.getGroupPolicy(holdingIdentity)?.let { toGroupInfo(holdingIdentity, it) }
    }

    override fun registerListener(groupPolicyListener: GroupPolicyListener) {
        groupPolicyProvider.registerListener { holdingIdentity, groupPolicy ->
            val groupInfo = toGroupInfo(holdingIdentity, groupPolicy)
            groupPolicyListener.groupAdded(groupInfo)
        }
    }

    private fun toGroupInfo(holdingIdentity: HoldingIdentity, groupPolicy: GroupPolicy): GroupPolicyListener.GroupInfo {
        val networkType = when (groupPolicy.p2pParameters.tlsPki) {
            P2PParameters.TlsPkiMode.STANDARD -> NetworkType.CORDA_5
            P2PParameters.TlsPkiMode.CORDA_4 -> NetworkType.CORDA_4
            else -> throw IllegalStateException("Invalid tlsPki value: ${groupPolicy.p2pParameters.tlsPki}")
        }

        val protocolModes = when (groupPolicy.p2pParameters.protocolMode) {
            P2PParameters.ProtocolMode.AUTH_ENCRYPT -> setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION)
            P2PParameters.ProtocolMode.AUTH -> setOf(ProtocolMode.AUTHENTICATION_ONLY)
            else -> throw IllegalStateException("Invalid protocol mode: ${groupPolicy.p2pParameters.protocolMode}")
        }

        val trustedCertificates = groupPolicy.p2pParameters.tlsTrustRoots.toList()

        return GroupPolicyListener.GroupInfo(holdingIdentity, networkType, protocolModes, trustedCertificates)
    }

}