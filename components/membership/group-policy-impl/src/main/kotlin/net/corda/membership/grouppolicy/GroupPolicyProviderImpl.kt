package net.corda.membership.grouppolicy

import net.corda.cpiinfo.read.CpiInfoReaderComponent
import net.corda.lifecycle.Lifecycle
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
import net.corda.membership.grouppolicy.factory.GroupPolicyFactory
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReaderComponent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.Collections

@Component(service = [GroupPolicyProvider::class])
class GroupPolicyProviderImpl @Activate constructor(
    @Reference(service = VirtualNodeInfoReaderComponent::class)
    private val virtualNodeInfoReader: VirtualNodeInfoReaderComponent,
    @Reference(service = CpiInfoReaderComponent::class)
    private val cpiInfoReader: CpiInfoReaderComponent,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : Lifecycle, GroupPolicyProvider {

    private var virtualNodeInfoCallbackHandle: AutoCloseable? = null
    private var registrationHandle: AutoCloseable? = null

    private val groupPolicyFactory = GroupPolicyFactory()

    private val groupPolicies: MutableMap<HoldingIdentity, GroupPolicy>
        get() = _groupPolicies ?: throw CordaRuntimeException(
            "Tried to access group policy information while the provider service is not running."
        )
    private var _groupPolicies: MutableMap<HoldingIdentity, GroupPolicy>? = null

    private val coordinator = lifecycleCoordinatorFactory
        .createCoordinator<GroupPolicyProvider>(::handleEvent)

    override fun getGroupPolicy(
        holdingIdentity: HoldingIdentity
    ) = lookupGroupPolicy(holdingIdentity) ?: parseGroupPolicy(holdingIdentity)

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()

    override var isRunning: Boolean = false

    private fun lookupGroupPolicy(
        holdingIdentity: HoldingIdentity
    ): GroupPolicy? = groupPolicies[holdingIdentity]

    private fun storeGroupPolicy(
        holdingIdentity: HoldingIdentity,
        groupPolicy: GroupPolicy
    ) {
        groupPolicies[holdingIdentity] = groupPolicy
    }

    /**
     * Parse the group policy string to a [GroupPolicy] object.
     *
     * [VirtualNodeInfoReaderComponent] is used to get the [VirtualNodeInfo], unless provided as a parameter. It may be
     * the case in a virtual node info callback where we are given the changed virtual node info.
     *
     * [CpiInfoReaderComponent] is used to get the CPI metadata containing the group policy for the CPI installed on
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
        val vNodeInfo = virtualNodeInfo
            ?: virtualNodeInfoReader.get(holdingIdentity)
        val metadata = vNodeInfo
            ?.cpi
            ?.let { cpiInfoReader.get(it) }
        return groupPolicyFactory
            .createGroupPolicy(metadata?.groupPolicy)
            .apply { storeGroupPolicy(holdingIdentity, this) }
    }

    /**
     * Handle lifecycle events.
     */
    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> handleStartEvent()
            is StopEvent -> handleStopEvent()
            is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
        }
    }

    /**
     * Start the component. This include setting the status to UP and creating a registration following the components
     * this component needs to function.
     */
    private fun handleStartEvent() {
        setStatusToUp(coordinator)

        registrationHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<VirtualNodeInfoReaderComponent>(),
                LifecycleCoordinatorName.forComponent<CpiInfoReaderComponent>()
            )
        )
    }

    /**
     * Handle stopping the component. This should set the status to DOWN and also close the registration handle.
     */
    private fun handleStopEvent() {
        setStatusToDown(coordinator)
        registrationHandle?.close()
    }

    /**
     * If any services we are following change status, this function reacts to that change.
     * If all of the followed services are UP then this service can start.
     * If any of the followed services are DOWN then this service should stop.
     */
    private fun handleRegistrationChangeEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        when (event.status) {
            LifecycleStatus.UP -> setStatusToUp(coordinator)
            else -> setStatusToDown(coordinator)
        }
    }

    /**
     * This function sets up the component so that it's status is UP.
     * This includes creating the data cache, registering a callback with the virtual node service, and updating the
     * component status.
     */
    private fun setStatusToUp(coordinator: LifecycleCoordinator) {
        _groupPolicies = Collections.synchronizedMap(mutableMapOf())

        /**
         * Register callback so that if a holding identity modifies their virtual node information, the group policy
         * for that holding identity will be parsed in case the virtual node change affected the group policy file.
         */
        virtualNodeInfoCallbackHandle = virtualNodeInfoReader.registerCallback { changed, snapshot ->
            changed.forEach { parseGroupPolicy(it, virtualNodeInfo = snapshot[it]) }
        }

        isRunning = true
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    /**
     * Sets the component status the DOWN.
     * This also closes the virtual node callback handle and clears cached data.
     * The component is not running after it has gone down.
     */
    private fun setStatusToDown(coordinator: LifecycleCoordinator) {
        coordinator.updateStatus(LifecycleStatus.DOWN)
        isRunning = false
        virtualNodeInfoCallbackHandle?.close()
        _groupPolicies = null
    }
}