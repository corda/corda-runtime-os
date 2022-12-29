package net.corda.p2p.linkmanager.integration.test.components

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.virtualnode.HoldingIdentity

internal class TestGroupPolicyProvider(
    coordinatorFactory: LifecycleCoordinatorFactory,
): GroupPolicyProvider, TestLifeCycle(
    coordinatorFactory,
    GroupPolicyProvider::class,
) {
    override fun getGroupPolicy(holdingIdentity: HoldingIdentity): GroupPolicy? {
        throw UnsupportedOperationException()
    }

    override fun registerListener(name: String, callback: (HoldingIdentity, GroupPolicy) -> Unit) {
    }
}