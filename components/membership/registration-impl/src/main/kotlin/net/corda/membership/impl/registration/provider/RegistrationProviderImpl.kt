package net.corda.membership.impl.registration.provider

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.createCoordinator
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.registration.provider.lifecycle.RegistrationProviderLifecycleHandler
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.provider.RegistrationProvider
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicyOption

@Component(service = [RegistrationProvider::class])
class RegistrationProviderImpl @Activate constructor(
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
) : RegistrationProvider {
    companion object {
        val logger = contextLogger()

        const val SERVICE_STARTING_LOG = "Registration provider starting."
        const val SERVICE_STOPPING_LOG = "Registration provider stopping."
        const val LOADING_SERVICE_LOG = "Attempting to load registration service: %s"

        const val NOT_RUNNING_ERROR =
            "Could not use RegistrationProvider because it is not currently running."
        const val NOT_UP_ERROR =
            "Could not use RegistrationProvider because it is running but not currently in status UP."
        const val SERVICE_NOT_FOUND_ERROR =
            "Could not load registration service: \"%s\". Service not found."
    }

    private val coordinator = lifecycleCoordinatorFactory.createCoordinator<RegistrationProvider>(
        RegistrationProviderLifecycleHandler(registrationServices)
    )

    override val isRunning get() = coordinator.isRunning

    override fun start() {
        logger.info(SERVICE_STARTING_LOG)
        coordinator.start()
    }

    override fun stop() {
        logger.info(SERVICE_STOPPING_LOG)
        coordinator.stop()
    }

    override fun get(holdingIdentity: HoldingIdentity): MemberRegistrationService? {
        serviceIsAvailable()
        return groupPolicyProvider.getGroupPolicy(holdingIdentity)?.let {
            getRegistrationService(it.registrationProtocol)
        }
    }

    private fun getRegistrationService(protocol: String): MemberRegistrationService {
        logger.debug(String.format(LOADING_SERVICE_LOG, protocol))
        val service = registrationServices.find { it.javaClass.name == protocol }
        registrationServiceIsFound(protocol, service)
        return service!!
    }

    private fun registrationServiceIsFound(expected: String, registrationService: MemberRegistrationService?) =
        logAndThrowError(String.format(SERVICE_NOT_FOUND_ERROR, expected)) { registrationService == null }

    private fun serviceIsAvailable() {
        serviceIsRunning()
        serviceIsUp()
    }

    private fun serviceIsRunning() = logAndThrowError(NOT_RUNNING_ERROR) { !isRunning }

    private fun serviceIsUp() = logAndThrowError(NOT_UP_ERROR) { coordinator.status != LifecycleStatus.UP }

    private fun logAndThrowError(msg: String, predicate: () -> Boolean) {
        if (predicate.invoke()) {
            logger.error(msg)
            throw CordaRuntimeException(msg)
        }
    }
}