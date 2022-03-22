package net.corda.membership.impl.registration.proxy

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.createCoordinator
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.registration.proxy.lifecycle.RegistrationProxyLifecycleHandler
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.membership.registration.proxy.RegistrationProxy
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicyOption

@Component(service = [RegistrationProxy::class])
class RegistrationProxyImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = GroupPolicyProvider::class)
    private val groupPolicyProvider: GroupPolicyProvider,
    @Reference(
        service = MemberRegistrationService::class,
        cardinality = ReferenceCardinality.AT_LEAST_ONE,
        policyOption = ReferencePolicyOption.GREEDY
    )
    private val registrationServices: List<MemberRegistrationService>
) : RegistrationProxy {

    private interface InnerRegistrationProxy {
        fun register(member: HoldingIdentity): MembershipRequestRegistrationResult
    }

    companion object {
        val logger = contextLogger()

        const val SERVICE_STARTING_LOG = "Registration proxy starting."
        const val SERVICE_STOPPING_LOG = "Registration proxy stopping."
        const val LOADING_SERVICE_LOG = "Attempting to load registration service: %s"

        const val SERVICE_NOT_FOUND_ERROR =
            "Could not load registration service: \"%s\". Service not found."
    }

    private val coordinator = lifecycleCoordinatorFactory.createCoordinator<RegistrationProxy>(
        RegistrationProxyLifecycleHandler(registrationServices, ::activate, ::deactivate)
    )

    private var impl: InnerRegistrationProxy = InactiveImpl()

    @VisibleForTesting
    fun activate(message: String) {
        logger.debug(message)
        impl = ActiveImpl(groupPolicyProvider, registrationServices)
        coordinator.updateStatus(LifecycleStatus.UP, message)
    }

    @VisibleForTesting
    fun deactivate(message: String) {
        logger.debug(message)
        coordinator.updateStatus(LifecycleStatus.DOWN, message)
        impl = InactiveImpl()
    }

    override val isRunning get() = coordinator.isRunning

    override fun start() {
        logger.info(SERVICE_STARTING_LOG)
        coordinator.start()
    }

    override fun stop() {
        logger.info(SERVICE_STOPPING_LOG)
        coordinator.stop()
    }

    override fun register(member: HoldingIdentity): MembershipRequestRegistrationResult = impl.register(member)

    private class InactiveImpl : InnerRegistrationProxy {
        override fun register(member: HoldingIdentity): MembershipRequestRegistrationResult =
            throw IllegalStateException("RegistrationProxy currently inactive.")

    }

    private class ActiveImpl(
        val groupPolicyProvider: GroupPolicyProvider,
        val registrationServices: List<MemberRegistrationService>
    ) : InnerRegistrationProxy {
        override fun register(member: HoldingIdentity): MembershipRequestRegistrationResult {
            val service = getRegistrationService(
                groupPolicyProvider.getGroupPolicy(member).registrationProtocol
            )
            return service.register(member)
        }

        private fun getRegistrationService(protocol: String): MemberRegistrationService {
            logger.debug(String.format(LOADING_SERVICE_LOG, protocol))
            val service = registrationServices.find { it.javaClass.name == protocol }
            registrationServiceIsFound(protocol, service)
            return service!!
        }

        private fun registrationServiceIsFound(expected: String, registrationService: MemberRegistrationService?) {
            if (registrationService == null) {
                val msg = String.format(SERVICE_NOT_FOUND_ERROR, expected)
                logger.error(msg)
                throw CordaRuntimeException(msg)
            }
        }
    }
}
