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
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory

internal class ForwardingGroupPolicyProvider(coordinatorFactory: LifecycleCoordinatorFactory,
                                             private val groupPolicyProvider: GroupPolicyProvider,
                                             virtualNodeInfoReadService: VirtualNodeInfoReadService,
                                             cpiInfoReadService: CpiInfoReadService,
                                             membershipQueryClient: MembershipQueryClient): LinkManagerGroupPolicyProvider {
    private companion object {
        private const val LISTENER_NAME = "link.manager.group.policy.listener"
        private const val KEY_STORE_TYPE = "PKCS12"
        private val logger = contextLogger()
    }
    private val certificateFactory = CertificateFactory.getInstance("X.509")

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
        groupPolicyProvider.registerListener(LISTENER_NAME) { holdingIdentity, groupPolicy ->
            toGroupInfo(holdingIdentity, groupPolicy)?.let { groupInfo ->
                groupPolicyListener.groupAdded(groupInfo)
            }
        }
    }
    @Suppress("ComplexMethod")
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

        val sessionTrustStore = groupPolicy.p2pParameters.sessionTrustRoots?.let { convertToKeyStore(it) ?: return null }
        if (sessionTrustStore == null && sessionPkiMode != P2PParameters.SessionPkiMode.NO_PKI) {
            logger.warn("Session trust roots is unexpectedly null in the group policy for $holdingIdentity.")
        }

        return GroupPolicyListener.GroupInfo(
            holdingIdentity,
            networkType,
            protocolModes,
            trustedCertificates,
            sessionPkiMode,
            sessionTrustStore
        )
    }

    private fun convertToKeyStore(pemCertificates: Collection<String>): KeyStore? {
        return KeyStore.getInstance(KEY_STORE_TYPE).also { keyStore ->
            keyStore.load(null, null)
            pemCertificates.withIndex().forEach { (index, pemCertificate) ->
                val certificate = ByteArrayInputStream(pemCertificate.toByteArray()).use {
                    try {
                        certificateFactory.generateCertificate(it)
                    } catch (except: CertificateException) {
                        logger.warn("Could not load session certificate: ${except.message}.")
                        return null
                    }
                }
                try {
                    keyStore.setCertificateEntry("session-$index", certificate)
                } catch (except: KeyStoreException) {
                    logger.warn("Could not load session certificate into keystore: ${except.message}.")
                    return null
                }
            }
        }
    }
}