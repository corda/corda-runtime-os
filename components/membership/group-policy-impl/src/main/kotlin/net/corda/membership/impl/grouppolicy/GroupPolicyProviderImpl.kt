package net.corda.membership.impl.grouppolicy

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.GroupPolicy
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.grouppolicy.factory.GroupPolicyParser
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.ConcurrentHashMap

@Component(service = [GroupPolicyProvider::class])
class GroupPolicyProviderImpl @Activate constructor(
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReader: CpiInfoReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : GroupPolicyProvider {

    companion object {
        val logger = contextLogger()
    }

    private var virtualNodeInfoCallbackHandle: AutoCloseable? = null
    private var registrationHandle: AutoCloseable? = null

    private val groupPolicyParser = GroupPolicyParser()

    private var groupPolicies: MutableMap<HoldingIdentity, GroupPolicy> = newCacheMap()
        get() = if (isRunning && isUp) {
            field
        } else {
            logger.error(
                "Service is in incorrect state for accessing group policies. " +
                        "Running: [$isRunning], Lifecycle status: [${coordinator.status}]"
            )
            throw CordaRuntimeException(
                "Tried to access group policy information while the provider service is not running or is not UP."
            )
        }

    private val coordinator = lifecycleCoordinatorFactory
        .createCoordinator<GroupPolicyProvider>(::handleEvent)

    override fun getGroupPolicy(
        holdingIdentity: HoldingIdentity
    ): GroupPolicy {
        return groupPolicies.computeIfAbsent(holdingIdentity) { parseGroupPolicy(holdingIdentity) }
    }

    private fun newCacheMap(): MutableMap<HoldingIdentity, GroupPolicy> = ConcurrentHashMap()

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()

    override val isRunning get() = coordinator.isRunning

    private val isUp get() = coordinator.status == LifecycleStatus.UP

    /**
     * Parse the group policy string to a [GroupPolicy] object.
     *
     * [VirtualNodeInfoReadService] is used to get the [VirtualNodeInfo], unless provided as a parameter. It may be
     * the case in a virtual node info callback where we are given the changed virtual node info.
     *
     * [CpiInfoReadService] is used to get the CPI metadata containing the group policy for the CPI installed on
     * the virtual node.
     *
     * The group policy is cached to simplify lookups later.
     *
     * @param holdingIdentity The holding identity of the member retrieving the group policy.
     * @param virtualNodeInfo if the VirtualNodeInfo is known, it can be passed in instead of getting this from the
     *  virtual node info reader.
     */
    private fun parseGroupPolicy(
        holdingIdentity: HoldingIdentity,
        virtualNodeInfo: VirtualNodeInfo? = null,
    ): GroupPolicy {
        val vNodeInfo = virtualNodeInfo ?: virtualNodeInfoReadService.get(holdingIdentity)
        val metadata = vNodeInfo?.cpiIdentifier?.let { cpiInfoReader.get(it) }
        return groupPolicyParser.parse(metadata?.groupPolicy)
    }

    /**
     * Handle lifecycle events.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.debug { "Group policy provider received event $event." }
        when (event) {
            is StartEvent -> handleStartEvent(coordinator)
            is StopEvent -> handleStopEvent(coordinator)
            is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
        }
    }

    /**
     * Start the component. This includes creating a registration following the component this component needs to
     * function and setting up the cache map.
     */
    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        logger.debug { "Group policy provider starting." }
        startDependencyRegistrationHandle(coordinator)
    }

    /**
     * Handle stopping the component. This should set the status to DOWN, clear the cache, and close the open
     * registration handles.
     */
    private fun handleStopEvent(coordinator: LifecycleCoordinator) {
        logger.debug { "Group policy provider stopping." }
        coordinator.updateStatus(LifecycleStatus.DOWN)
        virtualNodeInfoCallbackHandle?.close()
        registrationHandle?.close()
    }

    /**
     * If any services we are following change status, this function reacts to that change.
     * If all of the followed services are UP then this service can start.
     * If any of the followed services are DOWN then this service should stop.
     */
    private fun handleRegistrationChangeEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        logger.debug { "Group policy provider handling registration change. Event status: ${event.status}" }
        when (event.status) {
            LifecycleStatus.UP -> {
                groupPolicies = newCacheMap()
                startVirtualNodeHandle()
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            else -> {
                coordinator.updateStatus(LifecycleStatus.DOWN)
                virtualNodeInfoCallbackHandle?.close()
            }
        }
    }

    private fun startDependencyRegistrationHandle(coordinator: LifecycleCoordinator) {
        registrationHandle?.close()
        registrationHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
                LifecycleCoordinatorName.forComponent<CpiInfoReadService>()
            )
        )
    }

    /**
     * Register callback so that if a holding identity modifies their virtual node information, the
     * group policy for that holding identity will be parsed in case the virtual node change affected the
     * group policy file.
     */
    private fun startVirtualNodeHandle() {
        virtualNodeInfoCallbackHandle?.close()
        virtualNodeInfoCallbackHandle = virtualNodeInfoReadService.registerCallback { changed, snapshot ->
            if (isRunning && isUp) {
                changed.filter { snapshot[it] != null }.forEach {
                    parseGroupPolicy(it, virtualNodeInfo = snapshot[it])
                        .apply { groupPolicies[it] = this }
                }
            }
        }
    }
}
