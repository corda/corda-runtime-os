package net.corda.p2p.linkmanager.integration.stub

import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.virtualnode.HoldingIdentity

internal class GroupPolicyProviderStub : GroupPolicyProvider {
    override fun getP2PParameters(holdingIdentity: HoldingIdentity): GroupPolicy.P2PParameters? = throw UnsupportedOperationException()
    override fun getGroupPolicy(holdingIdentity: HoldingIdentity) = throw UnsupportedOperationException()

    override fun registerListener(
        name: String,
        callback: (HoldingIdentity, GroupPolicy) -> Unit,
    ) = run { }

    override val isRunning = true

    override fun start() = Unit

    override fun stop() = Unit
}
