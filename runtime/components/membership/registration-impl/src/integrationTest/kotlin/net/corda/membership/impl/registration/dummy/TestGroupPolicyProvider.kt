package net.corda.membership.impl.registration.dummy

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.LoggerFactory

interface TestGroupPolicyProvider : GroupPolicyProvider {
    fun putGroupPolicy(groupPolicy: GroupPolicy)
}

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [GroupPolicyProvider::class, TestGroupPolicyProvider::class])
class TestGroupPolicyProviderImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : TestGroupPolicyProvider {
    companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val UNIMPLEMENTED_FUNCTION = "Called unimplemented function for test service."
    }

    private val coordinator =
        coordinatorFactory.createCoordinator(LifecycleCoordinatorName.forComponent<GroupPolicyProvider>()) { event, coordinator ->
            if (event is StartEvent) {
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }

    private lateinit var policy: GroupPolicy

    override fun putGroupPolicy(groupPolicy: GroupPolicy) {
        policy = groupPolicy
    }

    override fun getGroupPolicy(holdingIdentity: HoldingIdentity) = policy

    override fun registerListener(name: String, callback: (HoldingIdentity, GroupPolicy) -> Unit) {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override val isRunning: Boolean
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        logger.info("TestGroupPolicyProvider starting.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("TestGroupPolicyProvider stopping.")
        coordinator.stop()
    }

}

class TestGroupPolicy : GroupPolicy {
    companion object {
        private const val UNIMPLEMENTED_FUNCTION = "Called unimplemented function for test service."
    }
    override val fileFormatVersion: Int
        get() = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)
    override val groupId: String
        get() = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)
    override val registrationProtocol: String
        get() = "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService"
    override val synchronisationProtocol: String
        get() = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)
    override val protocolParameters: GroupPolicy.ProtocolParameters
        get() = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)
    override val p2pParameters: GroupPolicy.P2PParameters
        get() = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)
    override val mgmInfo: GroupPolicy.MGMInfo
        get() = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)
    override val cipherSuite: GroupPolicy.CipherSuite
        get() = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)

}
