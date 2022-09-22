package net.corda.membership.impl.grouppolicy

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.membership.event.MembershipEvent
import net.corda.data.membership.event.registration.MgmOnboarded
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.membership.lib.grouppolicy.MGMGroupPolicy
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Membership.Companion.EVENT_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.ConcurrentHashMap

@Suppress("LongParameterList")
@Component(service = [GroupPolicyProvider::class])
class GroupPolicyProviderImpl @Activate constructor(
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReader: CpiInfoReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = GroupPolicyParser::class)
    private val groupPolicyParser: GroupPolicyParser,
    @Reference(service = MembershipQueryClient::class)
    private val membershipQueryClient: MembershipQueryClient,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
) : GroupPolicyProvider {


    /**
     * Private interface used for implementation swapping in response to lifecycle events.
     */
    private interface InnerGroupPolicyProvider : AutoCloseable {
        fun getGroupPolicy(holdingIdentity: HoldingIdentity): GroupPolicy?
    }

    private companion object {
        val logger = contextLogger()
        const val CONSUMER_GROUP = "membership.group.policy.provider.group"
    }
    private val groupPolicies: MutableMap<HoldingIdentity, GroupPolicy?> = ConcurrentHashMap()

    private var configHandle: AutoCloseable? = null

    private val coordinator = lifecycleCoordinatorFactory
        .createCoordinator<GroupPolicyProvider>(::handleEvent)

    private var impl: InnerGroupPolicyProvider = InactiveImpl

    private var dependencyServiceRegistration: RegistrationHandle? = null

    private var messagingConfig: SmartConfig? = null

    override fun getGroupPolicy(holdingIdentity: HoldingIdentity) = impl.getGroupPolicy(holdingIdentity)
    override fun registerListener(name: String, callback: (HoldingIdentity, GroupPolicy) -> Unit) {
        val listener = Listener(name, callback)
        messagingConfig?.also {
            listener.start(it)
        }
        listeners.put(name, listener)?.stop()
    }

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()

    override val isRunning get() = coordinator.isRunning

    private val listeners = ConcurrentHashMap<String, Listener>()

    /**
     * Handle lifecycle events.
     */
    @Suppress("ComplexMethod")
    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Group policy provider received event $event.")
        when (event) {
            is StartEvent -> {
                logger.info("Processing start event.")
                dependencyServiceRegistration?.close()
                dependencyServiceRegistration = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
                        LifecycleCoordinatorName.forComponent<CpiInfoReadService>(),
                        LifecycleCoordinatorName.forComponent<MembershipQueryClient>(),
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                    )
                )
            }
            is StopEvent -> {
                logger.info("Processing stop event.")
                deactivate("Stopping component.")
                dependencyServiceRegistration?.close()
                dependencyServiceRegistration = null
                listeners.values.forEach {
                    it.stop()
                }
                configHandle?.close()
                configHandle = null
            }
            is RegistrationStatusChangeEvent -> {
                logger.info("Group policy provider handling registration change. Event status: ${event.status}")
                if (event.status == LifecycleStatus.UP) {
                    logger.info("Dependency services are UP. Registering to receive configuration.")
                    configHandle?.close()
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(MESSAGING_CONFIG, BOOT_CONFIG)
                    )
                } else {
                    deactivate("Setting inactive state due to receiving registration status ${event.status}.")
                    listeners.values.forEach {
                        it.stop()
                    }
                }
            }
            is ConfigChangedEvent -> {
                activate("Received config, started subscriptions and setting status to UP.")
                messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
                listeners.values.forEach { listener ->
                    messagingConfig?.also {
                        listener.start(it)
                    }
                }
            }
        }
    }

    private fun activate(reason: String) {
        logger.debug(reason)
        coordinator.updateStatus(LifecycleStatus.UP, reason)
        swapImpl(ActiveImpl())
    }

    private fun deactivate(reason: String) {
        logger.debug(reason)
        coordinator.updateStatus(LifecycleStatus.DOWN, reason)
        swapImpl(InactiveImpl)
    }

    private fun swapImpl(newImpl: InnerGroupPolicyProvider) {
        val current = impl
        impl = newImpl
        current.close()
    }

    private object InactiveImpl : InnerGroupPolicyProvider {
        override fun getGroupPolicy(holdingIdentity: HoldingIdentity): GroupPolicy =
            throw IllegalStateException("Service is in incorrect state for accessing group policies.")

        override fun close() = Unit
    }

    private inner class ActiveImpl : InnerGroupPolicyProvider {
        private var virtualNodeInfoCallbackHandle: AutoCloseable = startVirtualNodeHandle()

        override fun getGroupPolicy(
            holdingIdentity: HoldingIdentity
        ) = try {
            groupPolicies.computeIfAbsent(holdingIdentity) { parseGroupPolicy(it) }
        } catch (e: BadGroupPolicyException) {
            logger.error("Could not parse group policy file for holding identity [$holdingIdentity].", e)
            null
        } catch (e: Throwable) {
            logger.error("Unexpected exception occurred when retrieving group policy file for " +
                    "holding identity [$holdingIdentity].", e)
            null
        }

        override fun close() {
            virtualNodeInfoCallbackHandle.close()
            groupPolicies.clear()
        }

        /**
         * Register callback so that if a holding identity modifies their virtual node information, the
         * group policy for that holding identity will be parsed in case the virtual node change affected the
         * group policy file.
         * Only true for members. For MGM identities we will wait until they are registered.
         */
        private fun startVirtualNodeHandle(): AutoCloseable =
            virtualNodeInfoReadService.registerCallback { changed, snapshot ->
                logger.info("Processing new snapshot after change in virtual node information.")
                changed.filter {
                    snapshot[it] != null
                }.forEach {
                    val groupPolicyToStore = try {
                        parseGroupPolicy(it, virtualNodeInfo = snapshot[it])
                    } catch (e: Exception) {
                        logger.error(
                            "Failure to parse group policy after change in virtual node info. " +
                                    "Check the format of the group policy in use for virtual node with ID [${it.shortHash}]. " +
                                    "Caught exception: ", e
                        )
                        logger.warn(
                            "Removing cached group policy due to problem when parsing update so it will be " +
                                    "repopulated on next read."
                        )
                        null
                    }
                    if(groupPolicyToStore == null) {
                        groupPolicies.remove(it)
                    } else if (groupPolicyToStore !is MGMGroupPolicy) {
                        logger.info("Caching group policy for member.")
                        groupPolicies[it] = groupPolicyToStore
                        listeners.values.forEach { listener ->
                            listener.callBack(it, groupPolicies[it]!!)
                        }
                        logger.info("Returning new group policy after change in virtual node information.")
                    }
                }
            }
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
    ): GroupPolicy? {
        val vNodeInfo = virtualNodeInfo ?: virtualNodeInfoReadService.get(holdingIdentity)
        if (vNodeInfo == null) {
            logger.warn("Could not get virtual node info for holding identity [${holdingIdentity}]")
        }
        val metadata = vNodeInfo?.cpiIdentifier?.let { cpiInfoReader.get(it) }
        if (metadata == null) {
            logger.warn(
                "Could not get CPI metadata for holding identity [${holdingIdentity}] and CPI with " +
                        "identifier [${vNodeInfo?.cpiIdentifier.toString()}]"
            )
        }
        fun persistedPropertyQuery(): LayeredPropertyMap? = try {
            membershipQueryClient.queryGroupPolicy(holdingIdentity).getOrThrow()
        } catch (e: MembershipQueryResult.QueryException) {
            logger.warn("Failed to retrieve persisted group policy properties.", e)
            null
        }
        return try {
            groupPolicyParser.parse(
                holdingIdentity,
                metadata?.groupPolicy,
                ::persistedPropertyQuery
            )
        } catch (e: BadGroupPolicyException) {
            logger.warn("Failed to parse group policy. Returning null.", e)
            null
        }
    }

    /**
     * Registers callback when MGM has finished its registration and has the final group policy persisted.
     * This will make sure we have the trust stores and other important information in the group policy ready.
     */
    internal inner class FinishedRegistrationsProcessor(
        private val callBack: (HoldingIdentity, GroupPolicy) -> Unit
    )
        : DurableProcessor<String, MembershipEvent> {
        @Suppress("NestedBlockDepth")
        override fun onNext(events: List<Record<String, MembershipEvent>>): List<Record<*, *>> {
            logger.info("Received event after mgm registration.")
            events.forEach {record ->
                try {
                    record.value
                        ?: throw CordaRuntimeException("MembershipEvent with record key: ${record.key} was null.")
                    when(record.value!!.event) {
                        is MgmOnboarded -> {
                            logger.info("Processing mgm onboarded event.")
                            val event = record.value!!.event as MgmOnboarded
                            val holdingIdentity = event.onboardedMgm.toCorda()
                            val gp = parseGroupPolicy(holdingIdentity)
                            if(gp == null || gp !is MGMGroupPolicy) {
                                groupPolicies.remove(holdingIdentity)
                                throw CordaRuntimeException(
                                    "Unable to get group policy for ${holdingIdentity.shortHash} or " +
                                            "we didn't receive an MGM group policy."
                                )
                            }
                            logger.info("Caching group policy for MGM.")
                            groupPolicies[holdingIdentity] = gp
                            callBack(holdingIdentity, gp)
                        }
                        else -> { logger.warn("Unhandled MembershipEvent was received.") }
                    }
                } catch(e: Exception) {
                    logger.warn("Could not process events, caused by: $e")
                }
            }

            // no need to publish new records after processing in our scenario
            return emptyList()
        }

        override val keyClass = String::class.java
        override val valueClass = MembershipEvent::class.java
    }
    private inner class Listener(
        private val name: String,
        val callBack: (HoldingIdentity, GroupPolicy) -> Unit,
    ) {
        private var subscription: Subscription<String, MembershipEvent>? = null

        fun start(messagingConfig: SmartConfig) {
            subscription?.close()
            subscription = subscriptionFactory.createDurableSubscription(
                SubscriptionConfig("$CONSUMER_GROUP-$name", EVENT_TOPIC),
                FinishedRegistrationsProcessor(callBack),
                messagingConfig,
                null
            ).also {
                it.start()
            }
        }

        fun stop() {
            subscription?.close()
            subscription = null
        }
    }
}
