package net.corda.p2p.linkmanager.grouppolicy

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.utils.PemCertificate
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.GroupParametersReaderService
import net.corda.p2p.NetworkType
import net.corda.p2p.crypto.ProtocolMode
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import kotlin.concurrent.thread

@Suppress("LongParameterList")
internal class ForwardingGroupPolicyProvider(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val groupPolicyProvider: GroupPolicyProvider,
    virtualNodeInfoReadService: VirtualNodeInfoReadService,
    cpiInfoReadService: CpiInfoReadService,
    membershipQueryClient: MembershipQueryClient,
    private val groupParametersReaderService: GroupParametersReaderService,
): LinkManagerGroupPolicyProvider {
    private companion object {
        const val LISTENER_NAME = "link.manager.group.policy.listener"
        val logger = contextLogger()
    }

    private val dependentChildren = setOf(
        LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
        LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
        LifecycleCoordinatorName.forComponent<CpiInfoReadService>(),
        LifecycleCoordinatorName.forComponent<GroupParametersReaderService>(),
        LifecycleCoordinatorName.forComponent<MembershipQueryClient>()
    )

    private val managedChildren = setOf(
        NamedLifecycle(groupPolicyProvider, LifecycleCoordinatorName.forComponent<GroupPolicyProvider>()),
        NamedLifecycle(virtualNodeInfoReadService, LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()),
        NamedLifecycle(cpiInfoReadService, LifecycleCoordinatorName.forComponent<CpiInfoReadService>()),
        NamedLifecycle(
            groupParametersReaderService,
            LifecycleCoordinatorName.forComponent<GroupParametersReaderService>(),
        ),
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
        groupPolicyProvider.registerListener(LISTENER_NAME) { holdingIdentity, groupPolicy ->
            toGroupInfo(holdingIdentity, groupPolicy)?.let { groupInfo ->
                println("QQQ Got group in link manager ${holdingIdentity.groupId}")
                val parameters = groupParametersReaderService.get(holdingIdentity)
                if (parameters == null) {
                    println("QQQ No parameters for ${holdingIdentity.groupId}")
                } else {
                    println("QQQ I have parameters for ${holdingIdentity.groupId}")
                    parameters.entries.forEach {
                        println("QQQ \t ${it.key} -> ${it.value}")
                    }
                }
                thread {
                    println("QQQ Trying in another thread...")
                    Thread.sleep(10000)
                    println("QQQ Woke up!")
                    val parameters2 = groupParametersReaderService.get(holdingIdentity)
                    if (parameters2 == null) {
                        println("QQQ still no parameters for ${holdingIdentity.groupId}")
                    } else {
                        println("QQQ now I have parameters for ${holdingIdentity.groupId}")
                        parameters2.entries.forEach {
                            println("QQQ \t ${it.key} -> ${it.value}")
                        }
                    }
                }
                groupPolicyListener.groupAdded(groupInfo)
            }
        }
    }

    private fun toGroupInfo(holdingIdentity: HoldingIdentity, groupPolicy: GroupPolicy): GroupPolicyListener.GroupInfo? {
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
        val sessionPkiMode = groupPolicy.p2pParameters.sessionPki

        val sessionTrustStorePem = groupPolicy.p2pParameters.sessionTrustRoots?.toList()
        validateTrustStoreUsingPkiMode(sessionTrustStorePem, sessionPkiMode, holdingIdentity)

        return GroupPolicyListener.GroupInfo(
            holdingIdentity,
            networkType,
            protocolModes,
            trustedCertificates,
            sessionPkiMode,
            sessionTrustStorePem
        )
    }

    private fun validateTrustStoreUsingPkiMode(
        sessionTrustStore: List<PemCertificate>?,
        sessionPkiMode: P2PParameters.SessionPkiMode,
        holdingIdentity: HoldingIdentity
    ) {
        if (sessionTrustStore == null && sessionPkiMode != P2PParameters.SessionPkiMode.NO_PKI) {
            logger.warn("Session trust roots is unexpectedly null in the group policy for $holdingIdentity. This can be caused by using " +
                    "the wrong PKI mode.")
        }
    }

}