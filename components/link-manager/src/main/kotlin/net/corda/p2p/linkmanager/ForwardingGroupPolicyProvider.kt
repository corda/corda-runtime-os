package net.corda.p2p.linkmanager

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.identity.HoldingIdentity
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.membership.GroupPolicy
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.p2p.NetworkType
import net.corda.p2p.crypto.ProtocolMode
import net.corda.virtualnode.read.VirtualNodeInfoReadService

@Suppress("LongParameterList")
internal class ForwardingGroupPolicyProvider(private val coordinatorFactory: LifecycleCoordinatorFactory,
                                             private val stubGroupPolicyProvider: StubGroupPolicyProvider,
                                             private val groupPolicyProvider: GroupPolicyProvider,
                                             private val thirdPartyComponentsMode: ThirdPartyComponentsMode,
                                             private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
                                             private val cpiInfoReadService: CpiInfoReadService): LinkManagerGroupPolicyProvider {

    private val dependentChildren = when(thirdPartyComponentsMode) {
        ThirdPartyComponentsMode.STUB -> setOf(stubGroupPolicyProvider.dominoTile.coordinatorName)
        ThirdPartyComponentsMode.REAL -> setOf(
            LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
            LifecycleCoordinatorName.forComponent<CpiInfoReadService>()
        )
    }
    private val managedChildren = when(thirdPartyComponentsMode) {
        ThirdPartyComponentsMode.STUB -> setOf(stubGroupPolicyProvider.dominoTile.toNamedLifecycle())
        ThirdPartyComponentsMode.REAL -> setOf(
            NamedLifecycle(groupPolicyProvider, LifecycleCoordinatorName.forComponent<GroupPolicyProvider>()),
            NamedLifecycle(virtualNodeInfoReadService, LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()),
            NamedLifecycle(cpiInfoReadService, LifecycleCoordinatorName.forComponent<CpiInfoReadService>())
        )
    }

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        coordinatorFactory,
        dependentChildren = dependentChildren,
        managedChildren = managedChildren
    )

    override fun getGroupInfo(holdingIdentity: HoldingIdentity): GroupPolicyListener.GroupInfo? {
        return if (thirdPartyComponentsMode == ThirdPartyComponentsMode.REAL) {
            val vNodeHoldingIdentity = net.corda.virtualnode.HoldingIdentity(holdingIdentity.x500Name, holdingIdentity.groupId)
            val groupPolicy = groupPolicyProvider.getGroupPolicy(vNodeHoldingIdentity)
            toGroupInfo(holdingIdentity, groupPolicy)
        } else {
            stubGroupPolicyProvider.getGroupInfo(holdingIdentity)
        }
    }

    override fun registerListener(groupPolicyListener: GroupPolicyListener) {
        when(thirdPartyComponentsMode) {
            ThirdPartyComponentsMode.REAL -> {
                groupPolicyProvider.registerListener { holdingIdentity, groupPolicy ->
                    val groupInfo = toGroupInfo(HoldingIdentity(holdingIdentity.x500Name, holdingIdentity.groupId), groupPolicy)
                    groupPolicyListener.groupAdded(groupInfo)
                }
            }
            ThirdPartyComponentsMode.STUB -> stubGroupPolicyProvider.registerListener(groupPolicyListener)
        }
    }

    private fun toGroupInfo(holdingIdentity: HoldingIdentity, groupPolicy: GroupPolicy): GroupPolicyListener.GroupInfo {
        val networkType = when (groupPolicy.tlsPki) {
            "C5" -> NetworkType.CORDA_5
            "C4" -> NetworkType.CORDA_4
            else -> throw IllegalStateException("Invalid tlsPki value: ${groupPolicy.tlsPki}")
        }

        val protocolModes = when (groupPolicy.p2pProtocolMode) {
            "AUTHENTICATED_ENCRYPTION" -> setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION)
            "AUTHENTICATION_ONLY" -> setOf(ProtocolMode.AUTHENTICATION_ONLY)
            else -> throw IllegalStateException("Invalid protocol mode: ${groupPolicy.p2pProtocolMode}")
        }

        val trustedCertificates = groupPolicy.tlsTrustStore.toList()

        return GroupPolicyListener.GroupInfo(holdingIdentity, networkType, protocolModes, trustedCertificates)
    }

}