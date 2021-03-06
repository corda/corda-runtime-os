package net.corda.membership.impl.synchronisation

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.command.synchronisation.SynchronisationCommand
import net.corda.data.membership.command.synchronisation.member.ProcessMembershipUpdates
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
import net.corda.membership.synchronisation.MemberSynchronisationService
import net.corda.membership.synchronisation.SynchronisationException
import net.corda.membership.synchronisation.SynchronisationProxy
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Membership.Companion.SYNCHRONISATION_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicyOption

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
        service = MemberSynchronisationService::class,
        cardinality = ReferenceCardinality.AT_LEAST_ONE,
        policyOption = ReferencePolicyOption.GREEDY
    )
    private val synchronisationServices: List<MemberSynchronisationService>
) : SynchronisationProxy {

    /**
     * Private interface used for implementation swapping in response to lifecycle events.
     */
    private interface InnerSynchronisationProxy {
        fun processMembershipUpdates(updates: ProcessMembershipUpdates)
    }

    private companion object {
        private val logger = contextLogger()

        const val SERVICE_STARTING_LOG = "Synchronisation proxy starting."
        const val SERVICE_STOPPING_LOG = "Synchronisation proxy stopping."
        const val LOADING_SERVICE_LOG = "Attempting to load synchronisation service: %s"
        const val SERVICE_NOT_FOUND_ERROR =
            "Could not load synchronisation service: \"%s\". Service not found."
        const val DOWN_REASON_STOPPED = "SynchronisationProxy was stopped."

        private const val CONSUMER_GROUP = "membership.synchronisation.group"
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

    override fun processMembershipUpdates(updates: ProcessMembershipUpdates) =
        impl.processMembershipUpdates(updates)

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
                            prefix = "Loaded member synchronisation services: [",
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
                    subscription?.close()
                    subscription = null
                }
            }
            is ConfigChangedEvent -> {
                val messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG)
                subscription?.close()
                subscription = subscriptionFactory.createDurableSubscription(
                    SubscriptionConfig(CONSUMER_GROUP, SYNCHRONISATION_TOPIC),
                    Processor(),
                    messagingConfig,
                    null
                ).also {
                    it.start()
                    subRegistration?.close()
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
            throw IllegalStateException("SynchronisationProxy currently inactive.")

    }

    private inner class ActiveImpl: InnerSynchronisationProxy {
        override fun processMembershipUpdates(updates: ProcessMembershipUpdates) {
            val viewOwningMember = updates.destination.toCorda()
            val protocol = try {
                groupPolicyProvider.getGroupPolicy(viewOwningMember)?.synchronisationProtocol
            } catch (e: BadGroupPolicyException) {
                val err =
                    "Failed to select correct synchronisation protocol due to problems retrieving the group policy."
                logger.error(err, e)
                throw SynchronisationProtocolSelectionException(err, e)
            } ?: throw SynchronisationProtocolSelectionException(
                "Could not find group policy file for holding identity: [${viewOwningMember.shortHash}]"
            )

            getSynchronisationService(protocol).processMembershipUpdates(updates)
        }

        private fun getSynchronisationService(protocol: String): MemberSynchronisationService {
            logger.debug(String.format(LOADING_SERVICE_LOG, protocol))
            val service = synchronisationServices.find { it.javaClass.name == protocol }
            if (service == null) {
                val err = String.format(SERVICE_NOT_FOUND_ERROR, protocol)
                logger.error(err)
                throw SynchronisationProtocolSelectionException(err)
            }
            return service
        }
    }

    private inner class Processor : DurableProcessor<String, SynchronisationCommand> {

        private val handlers = mapOf<Class<*>, SynchronisationHandler<*>>(
            ProcessMembershipUpdates::class.java to ProcessMembershipUpdatesHandler(),
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
                        else -> {
                            logger.warn("Unhandled synchronisation command received.")
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Failed to process synchronisation event.", e)
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
}
