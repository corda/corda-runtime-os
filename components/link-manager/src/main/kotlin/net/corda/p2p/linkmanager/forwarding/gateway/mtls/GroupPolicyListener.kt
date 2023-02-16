package net.corda.p2p.linkmanager.forwarding.gateway.mtls

import net.corda.data.p2p.mtls.gateway.ClientCertificateSubjects
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.virtualnode.HoldingIdentity

internal class GroupPolicyListener(
    private val publisher: PublisherWithDominoLogic,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val groupPolicyProvider: GroupPolicyProvider,
) : LifecycleWithDominoTile {
    private companion object {
        const val LISTENER_NAME = "link.manager.group.policy.listener.mtls.client.publisher"
    }

    fun startListen() {
        groupPolicyProvider.registerListener(LISTENER_NAME, ::groupAdded)
    }

    fun groupAdded(holdingIdentity: HoldingIdentity, groupPolicy: GroupPolicy) {
        val mgmClientCertificateSubject = groupPolicy.p2pParameters.mgmClientCertificateSubject?.toString()
        val value = if (mgmClientCertificateSubject == null) {
            null
        } else {
            ClientCertificateSubjects(mgmClientCertificateSubject)
        }
        val key = "group-policy-${holdingIdentity.shortHash}"
        val record = Record(
            Schemas.P2P.GATEWAY_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS,
            key,
            value,
        )
        publisher.publish(listOf(record))
    }

    override val dominoTile = ComplexDominoTile(
        this.javaClass.simpleName,
        lifecycleCoordinatorFactory,
        onStart = ::startListen,
        dependentChildren = listOf(publisher.dominoTile.coordinatorName),
        managedChildren = listOf(publisher.dominoTile.toNamedLifecycle())
    )
}
