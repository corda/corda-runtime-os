package net.corda.p2p.linkmanager

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.NetworkType
import net.corda.p2p.crypto.ProtocolMode
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda

@Suppress("LongParameterList")
internal class ForwardingGroupPolicyProvider(private val coordinatorFactory: LifecycleCoordinatorFactory,
                                             private val subscriptionFactory: SubscriptionFactory,
                                             private val messagingConfiguration: SmartConfig,
                                             private val groupPolicyProvider: GroupPolicyProvider,
                                             private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
                                             private val cpiInfoReadService: CpiInfoReadService,
                                             private val thirdPartyComponentsMode: ThirdPartyComponentsMode,
                                             private val membershipQueryClient: MembershipQueryClient):
    LinkManagerGroupPolicyProvider {

    private companion object {
        private val logger = contextLogger()
    }

    private val stubGroupPolicyProvider = StubGroupPolicyProvider(coordinatorFactory, subscriptionFactory, messagingConfiguration)

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
            NamedLifecycle(cpiInfoReadService, LifecycleCoordinatorName.forComponent<CpiInfoReadService>()),
            NamedLifecycle(membershipQueryClient, LifecycleCoordinatorName.forComponent<MembershipQueryClient>())
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
            groupPolicyProvider.getGroupPolicy(holdingIdentity.toCorda())?.let {
                toGroupInfo(holdingIdentity, it)
            }
        } else {
            stubGroupPolicyProvider.getGroupInfo(holdingIdentity)
        }
    }

    override fun registerListener(groupPolicyListener: GroupPolicyListener) {
        when(thirdPartyComponentsMode) {
            ThirdPartyComponentsMode.REAL -> {
                groupPolicyProvider.registerListener { holdingIdentity, groupPolicy ->
                    val groupInfo = toGroupInfo(holdingIdentity.toAvro(), groupPolicy)
                    groupPolicyListener.groupAdded(groupInfo)
                }
            }
            ThirdPartyComponentsMode.STUB -> stubGroupPolicyProvider.registerListener(groupPolicyListener)
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