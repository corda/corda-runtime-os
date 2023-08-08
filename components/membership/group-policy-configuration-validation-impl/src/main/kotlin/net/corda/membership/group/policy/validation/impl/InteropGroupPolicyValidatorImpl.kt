package net.corda.membership.group.policy.validation.impl

import net.corda.configuration.read.ConfigurationGetService
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.group.policy.validation.InteropGroupPolicyValidator
import net.corda.membership.group.policy.validation.InteropInvalidGroupPolicyException
import net.corda.membership.group.policy.validation.MembershipInvalidTlsTypeException
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsType
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component(service = [InteropGroupPolicyValidator::class])
class InteropGroupPolicyValidatorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = GroupPolicyParser::class)
    private val groupPolicyParser: GroupPolicyParser,
    @Reference(service = ConfigurationGetService::class)
    private val configurationGetService: ConfigurationGetService,
): InteropGroupPolicyValidator {
    private companion object {
        const val FOLLOW_STATUS_NAME = "INTEROP_GROUP_POLICY_VALIDATOR_IMPL_FOLLOW_STATUS"
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    private val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<InteropGroupPolicyValidator>()
    private val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName) { event, _ ->
        handleEvent(event)
    }

    private fun handleEvent(lifecycleEvent: LifecycleEvent) {
        when (lifecycleEvent) {
            is StartEvent -> handleStartEvent()
            is StopEvent -> handleStopEvent()
            is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(lifecycleEvent)
        }
    }

    private fun handleRegistrationChangeEvent(lifecycleEvent: RegistrationStatusChangeEvent) {
        coordinator.updateStatus(lifecycleEvent.status)
    }

    private fun handleStopEvent() {
        coordinator.closeManagedResources(setOf(FOLLOW_STATUS_NAME))
        coordinator.updateStatus(LifecycleStatus.DOWN)
    }

    private fun handleStartEvent() {
        coordinator.createManagedResource(FOLLOW_STATUS_NAME) {
            coordinator.followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                )
            )
        }
    }

    override fun validateGroupPolicy(groupPolicy: String) {
        val parsedGroupPolicy = try {
            groupPolicyParser.parseInteropGroupPolicy(groupPolicy)
        } catch (e: BadGroupPolicyException) {
            throw InteropInvalidGroupPolicyException("Could not parse the group policy: ${e.message}", e)
        } ?: return

        val groupPolicyTlsType = parsedGroupPolicy.p2pParameters.tlsType
        val clusterTlsType = TlsType.getClusterType(configurationGetService::getSmartConfig)
        if (groupPolicyTlsType != clusterTlsType) {
            throw MembershipInvalidTlsTypeException(
                "Group policy TLS type must be the same as the configuration of the cluster gateway",
            )
        }
    }

    override val isRunning
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}