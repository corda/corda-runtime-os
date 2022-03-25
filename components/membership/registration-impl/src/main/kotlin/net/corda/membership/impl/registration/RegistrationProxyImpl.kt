package net.corda.membership.impl.registration

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.exceptions.BadGroupPolicyException
import net.corda.membership.exceptions.RegistrationProtocolSelectionException
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.membership.registration.RegistrationProxy
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

    /**
     * Private interface used for implementation swapping in response to lifecycle events.
     */
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

        const val UP_REASON_READY = "All dependencies for RegistrationProxy are up so component is ready."
        const val DOWN_REASON_STOPPED = "RegistrationProxy was stopped."
        const val DOWN_REASON_NOT_READY = "Dependencies of RegistrationProxy are down."
    }

    private var dependencyStatusChangeHandle: AutoCloseable? = null
    private val dependencies = setOf(LifecycleCoordinatorName.forComponent<GroupPolicyProvider>()) +
            registrationServices.map { it.lifecycleCoordinatorName }

    private val coordinator = lifecycleCoordinatorFactory.createCoordinator<RegistrationProxy>(::handleEvent)

    private var impl: InnerRegistrationProxy = InactiveImpl()

    /**
     * Handle lifecycle events.
     */
    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                logger.info(
                    registrationServices
                        .joinToString(
                            prefix = "Loaded registration services: [",
                            postfix = "]",
                            transform = { it.javaClass.name }
                        )
                )
                registrationServices.forEach { it.start() }
                dependencyStatusChangeHandle?.close()
                dependencyStatusChangeHandle = coordinator.followStatusChangesByName(dependencies)
            }
            is StopEvent -> {
                deactivate(DOWN_REASON_STOPPED)
                dependencyStatusChangeHandle?.close()
            }
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.UP -> activate(UP_REASON_READY)
                    else -> deactivate(DOWN_REASON_NOT_READY)
                }

            }
        }
    }

    private fun activate(message: String) {
        logger.debug(message)
        impl = ActiveImpl(groupPolicyProvider, registrationServices)
        coordinator.updateStatus(LifecycleStatus.UP, message)
    }

    private fun deactivate(message: String) {
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
                try {
                    groupPolicyProvider.getGroupPolicy(member).registrationProtocol
                } catch (e: BadGroupPolicyException) {
                    val err =
                        "Failed to select correct registration protocol due to problems retrieving the group policy."
                    logger.error(err, e)
                    throw RegistrationProtocolSelectionException(err, e)
                }
            )
            return service.register(member)
        }

        private fun getRegistrationService(protocol: String): MemberRegistrationService {
            logger.debug(String.format(LOADING_SERVICE_LOG, protocol))
            val service = registrationServices.find { it.javaClass.name == protocol }
            if (service == null) {
                val err = String.format(SERVICE_NOT_FOUND_ERROR, protocol)
                logger.error(err)
                throw RegistrationProtocolSelectionException(err)
            }
            return service
        }
    }
}
