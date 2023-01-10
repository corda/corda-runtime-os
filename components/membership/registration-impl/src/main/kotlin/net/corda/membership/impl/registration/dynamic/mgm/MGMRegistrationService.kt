package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationGetService
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.CordaAvroSerializationFactory
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.groupparams.writer.service.GroupParametersWriterService
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.schema.validation.MembershipSchemaValidatorFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.MembershipRequestRegistrationOutcome
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.util.UUID

@Suppress("LongParameterList")
@Component(service = [MemberRegistrationService::class])
class MGMRegistrationService @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = MemberInfoFactory::class)
    private val memberInfoFactory: MemberInfoFactory,
    @Reference(service = MembershipPersistenceClient::class)
    private val membershipPersistenceClient: MembershipPersistenceClient,
    @Reference(service = LayeredPropertyMapFactory::class)
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = MembershipSchemaValidatorFactory::class)
    private val membershipSchemaValidatorFactory: MembershipSchemaValidatorFactory,
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = GroupParametersWriterService::class)
    private val groupParametersWriterService: GroupParametersWriterService,
    @Reference(service = GroupParametersFactory::class)
    private val groupParametersFactory: GroupParametersFactory,
    @Reference(service = ConfigurationGetService::class)
    private val configurationGetService: ConfigurationGetService,
) : MemberRegistrationService {
    /**
     * Private interface used for implementation swapping in response to lifecycle events.
     */
    private interface InnerRegistrationService : AutoCloseable {
        fun register(
            registrationId: UUID,
            member: HoldingIdentity,
            context: Map<String, String>
        ): MembershipRequestRegistrationResult
    }

    private companion object {
        val logger: Logger = contextLogger()
    }

    // for watching the config changes
    private var configHandle: AutoCloseable? = null

    // for checking the components' health
    private var componentHandle: RegistrationHandle? = null

    private var _publisher: Publisher? = null

    /**
     * Publisher for Kafka messaging. Recreated after every [MESSAGING_CONFIG] change.
     */
    private val publisher: Publisher
        get() = _publisher ?: throw IllegalArgumentException("Publisher is not initialized.")

    // Component lifecycle coordinator
    private val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName, ::handleEvent)

    private val clock: Clock = UTCClock()

    private var impl: InnerRegistrationService = InactiveImpl

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    private fun activate(coordinator: LifecycleCoordinator) {
        impl = ActiveImpl()
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun deactivate(coordinator: LifecycleCoordinator) {
        coordinator.updateStatus(LifecycleStatus.DOWN)
        impl.close()
        impl = InactiveImpl
    }

    override fun register(
        registrationId: UUID,
        member: HoldingIdentity,
        context: Map<String, String>
    ): MembershipRequestRegistrationResult = impl.register(registrationId, member, context)

    private object InactiveImpl : InnerRegistrationService {
        override fun register(
            registrationId: UUID,
            member: HoldingIdentity,
            context: Map<String, String>
        ): MembershipRequestRegistrationResult =
            MembershipRequestRegistrationResult(
                MembershipRequestRegistrationOutcome.NOT_SUBMITTED,
                "Registration failed. Reason: MGMRegistrationService is not running."
            )

        override fun close() = Unit
    }

    private inner class ActiveImpl : InnerRegistrationService {

        private val mgmRegistrationContextValidator = MGMRegistrationContextValidator(
            membershipSchemaValidatorFactory,
            configurationGetService = configurationGetService,
        )
        private val mgmRegistrationMemberInfoHandler = MGMRegistrationMemberInfoHandler(
            clock,
            cordaAvroSerializationFactory,
            cryptoOpsClient,
            keyEncodingService,
            memberInfoFactory,
            membershipPersistenceClient,
            platformInfoProvider,
            virtualNodeInfoReadService
        )
        private val mgmRegistrationGroupPolicyHandler = MGMRegistrationGroupPolicyHandler(
            layeredPropertyMapFactory,
            membershipPersistenceClient,
        )
        private val mgmRegistrationOutputPublisher = MGMRegistrationOutputPublisher { publisher }

        override fun register(
            registrationId: UUID,
            member: HoldingIdentity,
            context: Map<String, String>
        ): MembershipRequestRegistrationResult {
            return try {
                mgmRegistrationContextValidator.validate(context)

                val mgmInfo = mgmRegistrationMemberInfoHandler.buildAndPersist(
                    registrationId,
                    member,
                    context
                )

                mgmRegistrationGroupPolicyHandler.buildAndPersist(
                    member,
                    context
                )

                // Persist group parameters snapshot
                val groupParametersPersistenceResult =
                    membershipPersistenceClient.persistGroupParametersInitialSnapshot(member)
                if (groupParametersPersistenceResult is MembershipPersistenceResult.Failure) {
                    throw MembershipPersistenceException(groupParametersPersistenceResult.errorMsg)
                }

                // Publish group parameters to Kafka
                val groupParameters = groupParametersFactory.create(groupParametersPersistenceResult.getOrThrow())
                groupParametersWriterService.put(member, groupParameters)

                mgmRegistrationOutputPublisher.publish(mgmInfo)

                MembershipRequestRegistrationResult(MembershipRequestRegistrationOutcome.SUBMITTED)
            } catch (ex: MGMRegistrationContextValidationException) {
                buildNotSubmittedResponse(ex.reason)
            } catch (ex: MGMRegistrationMemberInfoHandlingException) {
                buildNotSubmittedResponse(ex.reason)
            } catch (ex: MGMRegistrationGroupPolicyHandlingException) {
                buildNotSubmittedResponse(ex.reason)
            } catch (ex: MGMRegistrationOutputPublisherException){
                buildNotSubmittedResponse(ex.reason)
            } catch (e: Exception) {
                buildNotSubmittedResponse("Registration failed. Reason: ${e.message}")
            }
        }

        private fun buildNotSubmittedResponse(reason: String): MembershipRequestRegistrationResult {
            return MembershipRequestRegistrationResult(
                MembershipRequestRegistrationOutcome.NOT_SUBMITTED,
                reason
            )
        }

        override fun close() {
            publisher.close()
        }
    }

    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> handleStartEvent(coordinator)
            is StopEvent -> handleStopEvent(coordinator)
            is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
            is ConfigChangedEvent -> handleConfigChange(event, coordinator)
        }
    }

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        componentHandle?.close()
        componentHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
            )
        )
    }

    private fun handleStopEvent(coordinator: LifecycleCoordinator) {
        deactivate(coordinator)
        componentHandle?.close()
        componentHandle = null
        configHandle?.close()
        configHandle = null
        _publisher?.close()
        _publisher = null
    }

    private fun handleRegistrationChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        when (event.status) {
            LifecycleStatus.UP -> {
                configHandle?.close()
                configHandle = configurationReadService.registerComponentForUpdates(
                    coordinator,
                    setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                )
            }
            else -> {
                deactivate(coordinator)
                configHandle?.close()
            }
        }
    }

    // re-creates the publisher with the new config
    // sets the lifecycle status to UP when the publisher is ready for the first time
    private fun handleConfigChange(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        logger.info("Handling config changed event.")
        _publisher?.close()
        _publisher = publisherFactory.createPublisher(
            PublisherConfig("mgm-registration-service"),
            event.config.getConfig(MESSAGING_CONFIG)
        )
        _publisher?.start()
        activate(coordinator)
    }
}
