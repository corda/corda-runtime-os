package net.corda.membership.grouppolicy

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity

class TestGroupPolicyProvider(
    coordinatorFactory: LifecycleCoordinatorFactory
) : GroupPolicyProvider {

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<GroupPolicyProvider>{ event, coordinator ->
        if(event is StartEvent) { coordinator.updateStatus(LifecycleStatus.UP) }
    }

    companion object {
        val logger = contextLogger()
        private const val UNIMPLEMENTED_FUNCTION = "Called unimplemented function for test service"
    }

    override fun getGroupPolicy(holdingIdentity: HoldingIdentity): GroupPolicy? {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun registerListener(name: String, callback: (HoldingIdentity, GroupPolicy) -> Unit) {

    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning


    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }
}