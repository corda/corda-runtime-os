package net.corda.membership.impl.synchronisation

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.command.synchronisation.SynchronisationCommand
import net.corda.data.membership.command.synchronisation.member.ProcessMembershipUpdates
import net.corda.data.membership.command.synchronisation.mgm.ProcessSyncRequest
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
import net.corda.membership.lib.exceptions.SynchronisationProtocolSelectionException
import net.corda.membership.lib.exceptions.SynchronisationProtocolTypeException
import net.corda.membership.synchronisation.MemberSynchronisationService
import net.corda.membership.synchronisation.MgmSynchronisationService
import net.corda.membership.synchronisation.SynchronisationException
import net.corda.membership.synchronisation.SynchronisationProxy
import net.corda.membership.synchronisation.SynchronisationService
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Membership.Companion.SYNCHRONIZATION_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicyOption
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
@Component(service = [SynchronisationProxy::class])
class SynchronisationProxyImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = GroupPolicyProvider::class)
    private val groupPolicyProvider: GroupPolicyProvider,
    @Reference(
        service = SynchronisationService::class,
        cardinality = ReferenceCardinality.AT_LEAST_ONE,
        policyOption = ReferencePolicyOption.GREEDY
    )
    private val synchronisationServices: List<SynchronisationService>
) : SynchronisationProxy {

    /**
     * Private interface used for implementation swapping in response to lifecycle events.
     */
    private interface InnerSynchronisationProxy {
        /**
         * Retrieves the appropriate instance of [MemberSynchronisationService] for a holding identity as specified in the CPI
         * configuration, and delegates the processing of membership updates to it.
         *
         * @param updates Data package distributed by the MGM containing membership updates.
         *
         * @throws [SynchronisationProtocolSelectionException] if the synchronisation protocol could not be selected.
         * @throws [SynchronisationProtocolTypeException] if the configured protocol is not an [MemberSynchronisationService].
         */
        fun processMembershipUpdates(updates: ProcessMembershipUpdates)

        /**
         * Retrieves the appropriate instance of [MgmSynchronisationService] for a holding identity as specified in the CPI
         * configuration, and delegates the processing of membership sync requests to it.
         *
         * @param request The sync request which needs to be processed.
         *
         * @throws [SynchronisationProtocolSelectionException] if the synchronisation protocol could not be selected.
         * @throws [SynchronisationProtocolTypeException] if the configured protocol is not an [MgmSynchronisationService].
         */
        fun processSyncRequest(request: ProcessSyncRequest)
    }

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        const val SERVICE_STARTING_LOG = "Synchronisation proxy starting."
        const val SERVICE_STOPPING_LOG = "Synchronisation proxy stopping."
        const val LOADING_SERVICE_LOG = "Attempting to load synchronisation service: %s"
        const val SERVICE_NOT_FOUND_ERROR =
            "Could not load synchronisation service: \"%s\". Service not found."
        const val DOWN_REASON_STOPPED = "SynchronisationProxy was stopped."
        const val CONSUMER_GROUP = "membership.synchronisation.group"
        const val FAILED_SYNC_MSG = "Failed to process synchronisation event."
        const val INACTIVE_SERVICE = "SynchronisationProxy currently inactive."
    }

    private val coordinator =
        coordinatorFactory.createCoordinator<SynchronisationProxy>(::handleEvent)

    private var dependencyServiceRegistration: RegistrationHandle? = null
    private var subRegistration: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null
    private val dependencies = setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
        LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
    ) + synchronisationServices.map { it.lifecycleCoordinatorName }

    private var subscription: Subscription<String, SynchronisationCommand>? = null

    private var impl: InnerSynchronisationProxy = InactiveImpl

    fun processMembershipUpdates(updates: ProcessMembershipUpdates) =
        impl.processMembershipUpdates(updates)

    fun processSyncRequest(request: ProcessSyncRequest) =
        impl.processSyncRequest(request)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.info(SERVICE_STARTING_LOG)
        coordinator.start()
    }

    override fun stop() {
        logger.info(SERVICE_STOPPING_LOG)
        coordinator.stop()
    }

    @Suppress("ComplexMethod")
    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Received event {}", event)
        when (event) {
            is StartEvent -> {
                logger.info("Processing start event.")
                logger.info(
                    synchronisationServices
                        .joinToString(
                            prefix = "Loaded synchronisation services: [",
                            postfix = "]",
                            transform = { it.javaClass.name }
                        )
                )
                synchronisationServices.forEach { it.start() }
                dependencyServiceRegistration?.close()
                dependencyServiceRegistration = coordinator.followStatusChangesByName(dependencies)
            }
            is StopEvent -> {
                deactivate(DOWN_REASON_STOPPED)
                dependencyServiceRegistration?.close()
                dependencyServiceRegistration = null
                subRegistration?.close()
                subRegistration = null
                configHandle?.close()
                configHandle = null
                subscription?.close()
                subscription = null
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    if (event.registration == dependencyServiceRegistration) {
                        logger.info("Dependency services are UP. Registering to receive configuration.")
                        configHandle?.close()
                        configHandle = configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(ConfigKeys.MESSAGING_CONFIG, ConfigKeys.BOOT_CONFIG)
                        )
                    } else if (event.registration == subRegistration) {
                        activate("Received config, started subscriptions and setting status to UP")
                    }
                } else {
                    deactivate("Setting inactive state due to receiving registration status ${event.status}")
                    subRegistration?.close()
                    subRegistration = null
                    subscription?.close()
                    subscription = null
                }
            }
            is ConfigChangedEvent -> {
                val messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG)
                subRegistration?.close()
                subRegistration = null
                subscription?.close()
                subscription = subscriptionFactory.createDurableSubscription(
                    SubscriptionConfig(CONSUMER_GROUP, SYNCHRONIZATION_TOPIC),
                    Processor(),
                    messagingConfig,
                    null
                ).also {
                    it.start()
                    subRegistration = coordinator.followStatusChangesByName(setOf(it.subscriptionName))
                }

            }
        }
    }

    private fun activate(message: String) {
        logger.debug(message)
        impl = ActiveImpl()
        coordinator.updateStatus(LifecycleStatus.UP, message)
    }

    private fun deactivate(message: String) {
        logger.debug(message)
        coordinator.updateStatus(LifecycleStatus.DOWN, message)
        impl = InactiveImpl
    }

    private object InactiveImpl : InnerSynchronisationProxy {
        override fun processMembershipUpdates(updates: ProcessMembershipUpdates) =
            throw IllegalStateException(INACTIVE_SERVICE)

        override fun processSyncRequest(request: ProcessSyncRequest) =
            throw IllegalStateException(INACTIVE_SERVICE)

    }

    private inner class ActiveImpl: InnerSynchronisationProxy {
        override fun processMembershipUpdates(updates: ProcessMembershipUpdates) {
            val service = getSynchronisationService(
                updates.synchronisationMetaData.member.toCorda(),
                MemberSynchronisationService::class.java
            )
            service.processMembershipUpdates(updates)
        }

        override fun processSyncRequest(request: ProcessSyncRequest) {
            val service = getSynchronisationService(
                request.synchronisationMetaData.mgm.toCorda(),
                MgmSynchronisationService::class.java
            )
            service.processSyncRequest(request)
        }

        private fun selectSynchronisationProtocol(viewOwningMember: HoldingIdentity): String =
            try {
                groupPolicyProvider.getGroupPolicy(viewOwningMember)?.synchronisationProtocol
            } catch (e: BadGroupPolicyException) {
                val err =
                    "Failed to select correct synchronisation protocol due to problems retrieving the group policy."
                logger.error(err, e)
                throw SynchronisationProtocolSelectionException(err, e)
            } catch (e: IllegalStateException) {
                logger.warn("Failed to select correct registration protocol due to problems retrieving the group policy.", e)
                null
            } ?: throw SynchronisationProtocolSelectionException(
                "Could not find group policy file for holding identity: [${viewOwningMember.shortHash}]"
            )

        @Suppress("unchecked_cast")
        private fun <T: SynchronisationService> getSynchronisationService(viewOwningMember: HoldingIdentity, serviceType: Class<T>): T {
            val protocol = selectSynchronisationProtocol(viewOwningMember)
            logger.debug(String.format(LOADING_SERVICE_LOG, protocol))
            val service = synchronisationServices.find { it.javaClass.name == protocol }
            if (service == null) {
                val err = String.format(SERVICE_NOT_FOUND_ERROR, protocol)
                logger.error(err)
                throw SynchronisationProtocolSelectionException(err)
            }
            if(!serviceType.isAssignableFrom(service::class.java)) {
                throw SynchronisationProtocolTypeException("Wrong synchronisation service type was configured in group policy file.")
            }
            return service as T
        }
    }

    internal inner class Processor : DurableProcessor<String, SynchronisationCommand> {
        private val handlers = mapOf<Class<*>, SynchronisationHandler<*>>(
            ProcessMembershipUpdates::class.java to ProcessMembershipUpdatesHandler(),
            ProcessSyncRequest::class.java to ProcessSyncRequestHandler(),
        )

        override fun onNext(events: List<Record<String, SynchronisationCommand>>): List<Record<*, *>> {
            events.forEach { record ->
                try {
                    record.value
                        ?: throw SynchronisationException("SynchronisationCommand with record key: ${record.key} was null.")
                    when (record.value!!.command) {
                        is ProcessMembershipUpdates -> {
                            logger.info("Received process membership updates command.")
                            handlers[ProcessMembershipUpdates::class.java]?.invoke(record)
                        }
                        is ProcessSyncRequest -> {
                            logger.info("Received process synchronisation updates command.")
                            handlers[ProcessSyncRequest::class.java]?.invoke(record)
                        }
                        else -> {
                            logger.warn("Unhandled synchronisation command received.")
                        }
                    }
                } catch (e: SynchronisationProtocolTypeException) {
                    logger.warn(FAILED_SYNC_MSG, e)
                } catch (e: Exception) {
                    logger.error(FAILED_SYNC_MSG, e)
                }
            }
            return emptyList()
        }

        override val keyClass = String::class.java
        override val valueClass = SynchronisationCommand::class.java
    }

    interface SynchronisationHandler<T> {
        fun invoke(event: Record<String, SynchronisationCommand>) {
            val command = event.value?.command
            if (commandType.isInstance(command)) {
                @Suppress("unchecked_cast")
                return invoke(command as T)
            } else {
                throw CordaRuntimeException("Invalid command: $command")
            }
        }

        fun invoke(command: T)

        val commandType: Class<T>
    }

    private inner class ProcessMembershipUpdatesHandler : SynchronisationHandler<ProcessMembershipUpdates> {
        override fun invoke(command: ProcessMembershipUpdates) {
            processMembershipUpdates(command)
        }

        override val commandType: Class<ProcessMembershipUpdates>
            get() = ProcessMembershipUpdates::class.java

    }

    private inner class ProcessSyncRequestHandler : SynchronisationHandler<ProcessSyncRequest> {
        override fun invoke(command: ProcessSyncRequest) {
            processSyncRequest(command)
        }

        override val commandType: Class<ProcessSyncRequest>
            get() = ProcessSyncRequest::class.java

    }
}
