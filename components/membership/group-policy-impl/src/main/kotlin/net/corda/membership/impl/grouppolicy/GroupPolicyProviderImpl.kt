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
import net.corda.v5.base.util.contextLogger
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

    /**
     * Private interface used for implementation swapping in response to lifecycle events.
     */
    private interface InnerGroupPolicyProvider : AutoCloseable {
        fun getGroupPolicy(holdingIdentity: HoldingIdentity): GroupPolicy
    }

    companion object {
        val logger = contextLogger()
    }

    private var registrationHandle: AutoCloseable? = null

    private val coordinator = lifecycleCoordinatorFactory
        .createCoordinator<GroupPolicyProvider>(::handleEvent)

    private var impl: InnerGroupPolicyProvider = InactiveImpl()

    override fun getGroupPolicy(holdingIdentity: HoldingIdentity) = impl.getGroupPolicy(holdingIdentity)

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()

    override val isRunning get() = coordinator.isRunning

    /**
     * Handle lifecycle events.
     */
    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Group policy provider received event $event.")
        when (event) {
            is StartEvent -> {
                logger.info("Group policy provider starting.")
                registrationHandle?.close()
                registrationHandle = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
                        LifecycleCoordinatorName.forComponent<CpiInfoReadService>()
                    )
                )
            }
            is StopEvent -> {
                logger.info("Group policy provider stopping.")
                deactivate("Stopping component.")
                registrationHandle?.close()
            }
            is RegistrationStatusChangeEvent -> {
                logger.info("Group policy provider handling registration change. Event status: ${event.status}")
                when (event.status) {
                    LifecycleStatus.UP -> {
                        activate("All dependencies are UP.")
                    }
                    else -> {
                        deactivate("All dependencies are not UP.")
                    }
                }
            }
        }
    }

    private fun activate(reason: String) {
        swapImpl(ActiveImpl(virtualNodeInfoReadService, cpiInfoReader))
        updateStatus(LifecycleStatus.UP, reason)
    }

    private fun deactivate(reason: String) {
        updateStatus(LifecycleStatus.DOWN, reason)
        swapImpl(InactiveImpl())
    }

    private fun updateStatus(status: LifecycleStatus, reason: String) {
        if(coordinator.status != status) {
            coordinator.updateStatus(status, reason)
        }
    }

    private fun swapImpl(newImpl: InnerGroupPolicyProvider) {
        val current = impl
        impl = newImpl
        current.close()
    }

    private class InactiveImpl : InnerGroupPolicyProvider {
        override fun getGroupPolicy(holdingIdentity: HoldingIdentity): GroupPolicy =
            throw IllegalStateException("Service is in incorrect state for accessing group policies.")

        override fun close() = Unit
    }

    private class ActiveImpl(
        private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
        private val cpiInfoReader: CpiInfoReadService
    ) : InnerGroupPolicyProvider {
        private val groupPolicyParser = GroupPolicyParser()

        private val groupPolicies: MutableMap<HoldingIdentity, GroupPolicy> = ConcurrentHashMap()

        private var virtualNodeInfoCallbackHandle: AutoCloseable = startVirtualNodeHandle()

        override fun getGroupPolicy(
            holdingIdentity: HoldingIdentity
        ) = groupPolicies.computeIfAbsent(holdingIdentity) { parseGroupPolicy(it) }

        override fun close() {
            virtualNodeInfoCallbackHandle.close()
            groupPolicies.clear()
        }

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
            if(vNodeInfo == null) {
                logger.warn("Could not get virtual node info for holding identity [${holdingIdentity}]")
            }
            val metadata = vNodeInfo?.cpiIdentifier?.let { cpiInfoReader.get(it) }
            if(metadata == null) {
                logger.warn("Could not get CPI metadata for holding identity [${holdingIdentity}] and CPI with " +
                        "identifier [${vNodeInfo?.cpiIdentifier.toString()}]")
            }
            return groupPolicyParser.parse(metadata?.groupPolicy)
        }

        /**
         * Register callback so that if a holding identity modifies their virtual node information, the
         * group policy for that holding identity will be parsed in case the virtual node change affected the
         * group policy file.
         */
        private fun startVirtualNodeHandle(): AutoCloseable =
            virtualNodeInfoReadService.registerCallback { changed, snapshot ->
                logger.info("Processing new snapshot after change in virtual node information.")
                changed.filter { snapshot[it] != null }.forEach {
                    try {
                        groupPolicies[it] = parseGroupPolicy(it, virtualNodeInfo = snapshot[it])
                    } catch (e: Exception) {
                        logger.info(
                            "Failure to parse group policy after change in virtual node info. " +
                                    "Check the format of the group policy in use for virtual node with ID [${it.id}]. " +
                                    "Caught exception: ", e
                        )
                        groupPolicies.remove(it)
                        logger.info("Removed cached group policy due to problem when parsing update so it will be " +
                                "repopulated on next read.")
                    }
                }
            }
    }
}
