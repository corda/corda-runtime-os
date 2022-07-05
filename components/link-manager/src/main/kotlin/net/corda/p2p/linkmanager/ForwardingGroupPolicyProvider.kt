package net.corda.p2p.linkmanager

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.membership.lib.GroupPolicy
import net.corda.membership.grouppolicy.GroupPolicyProvider
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
                                             private val thirdPartyComponentsMode: ThirdPartyComponentsMode):
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
            try {
                val groupPolicy = groupPolicyProvider.getGroupPolicy(holdingIdentity.toCorda())
                toGroupInfo(holdingIdentity, groupPolicy)
            } catch (e: Exception) {
                logger.error("Received exception while trying to retrieve group policy for identity $holdingIdentity.")
                null
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