package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.configuration.read.ConfigurationGetService
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
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
import net.corda.membership.lib.MemberInfoExtension.Companion.CREATION_TIME
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.schema.validation.MembershipSchemaValidatorFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.registration.ExpirationProcessor
import net.corda.membership.registration.InvalidMembershipRegistrationException
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.NotReadyMembershipRegistrationException
import net.corda.messaging.api.records.Record
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.UUID

@Suppress("LongParameterList")
@Component(service = [MemberRegistrationService::class])
class MGMRegistrationService @Activate constructor(
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
    @Reference(service = MembershipQueryClient::class)
    private val membershipQueryClient: MembershipQueryClient,
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
    @Reference(service = ConfigurationGetService::class)
    private val configurationGetService: ConfigurationGetService,
    @Reference(service = ExpirationProcessor::class)
    private val expirationProcessor: ExpirationProcessor,
) : MemberRegistrationService {

    /**
     * Private interface used for implementation swapping in response to lifecycle events.
     */
    private interface InnerRegistrationService {
        fun register(
            registrationId: UUID,
            member: HoldingIdentity,
            context: Map<String, String>
        ): Collection<Record<*, *>>
    }

    // for checking the components' health
    private var componentHandle: RegistrationHandle? = null

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
        impl = InactiveImpl
    }

    override fun register(
        registrationId: UUID,
        member: HoldingIdentity,
        context: Map<String, String>
    ) = impl.register(registrationId, member, context)

    private object InactiveImpl : InnerRegistrationService {
        override fun register(
            registrationId: UUID,
            member: HoldingIdentity,
            context: Map<String, String>
        ) = throw NotReadyMembershipRegistrationException(
            "Registration failed. Reason: MGMRegistrationService is not running."
        )
    }

    private inner class ActiveImpl : InnerRegistrationService {

        private val mgmRegistrationRequestHandler = MGMRegistrationRequestHandler(
            cordaAvroSerializationFactory,
            membershipPersistenceClient,
            membershipQueryClient
        )
        private val mgmRegistrationContextValidator = MGMRegistrationContextValidator(
            membershipSchemaValidatorFactory,
            configurationGetService = configurationGetService,
            clock = UTCClock(),
        )
        private val mgmRegistrationMemberInfoHandler = MGMRegistrationMemberInfoHandler(
            clock,
            cryptoOpsClient,
            keyEncodingService,
            memberInfoFactory,
            membershipPersistenceClient,
            membershipQueryClient,
            platformInfoProvider,
            virtualNodeInfoReadService,
            cordaAvroSerializationFactory,
        )
        private val mgmRegistrationGroupPolicyHandler = MGMRegistrationGroupPolicyHandler(
            layeredPropertyMapFactory,
            membershipPersistenceClient,
            membershipQueryClient,
        )
        private val mgmRegistrationOutputPublisher = MGMRegistrationOutputPublisher(memberInfoFactory)

        override fun register(
            registrationId: UUID,
            member: HoldingIdentity,
            context: Map<String, String>
        ): Collection<Record<*, *>> {
            return try {
                val lastRegistrationRequest = mgmRegistrationRequestHandler.getLastRegistrationRequest(member)
                val mgmInfo = if (lastRegistrationRequest != null) {
                    val lastMemberInfo = mgmRegistrationMemberInfoHandler.queryForMGMMemberInfo(member)
                    val newSerial = calculateSerial(lastMemberInfo.serial, lastRegistrationRequest.serial)
                    val mgmInfo = mgmRegistrationMemberInfoHandler.buildMgmMemberInfo(
                        member,
                        context,
                        newSerial,
                        lastMemberInfo.mgmProvidedContext[CREATION_TIME],
                    )
                    mgmRegistrationContextValidator.validateMemberContext(mgmInfo, lastMemberInfo)
                    val lastGroupPolicy = mgmRegistrationGroupPolicyHandler.getLastGroupPolicy(member)
                    mgmRegistrationContextValidator.validateGroupPolicy(context, lastGroupPolicy)
                    mgmRegistrationMemberInfoHandler.persistMgmMemberInfo(member, mgmInfo)
                    mgmRegistrationRequestHandler.persistRegistrationRequest(
                        registrationId,
                        member,
                        context,
                        newSerial - 1,
                    )
                    mgmInfo
                } else {
                    mgmRegistrationContextValidator.validate(context)
                    val mgmInfo = mgmRegistrationMemberInfoHandler.buildMgmMemberInfo(member, context)
                    mgmRegistrationMemberInfoHandler.persistMgmMemberInfo(member, mgmInfo)
                    mgmRegistrationGroupPolicyHandler.buildAndPersist(
                        member,
                        context
                    )

                    // Persist group parameters snapshot
                    val groupParametersPersistenceResult =
                        membershipPersistenceClient.persistGroupParametersInitialSnapshot(member)
                            .execute()
                    if (groupParametersPersistenceResult is MembershipPersistenceResult.Failure) {
                        throw NotReadyMembershipRegistrationException(groupParametersPersistenceResult.errorMsg)
                    }
                    mgmRegistrationRequestHandler.persistRegistrationRequest(registrationId, member, context)

                    // Publish group parameters to Kafka
                    val groupParameters = groupParametersPersistenceResult.getOrThrow()
                    groupParametersWriterService.put(member, groupParameters)
                    mgmInfo
                }

                expirationProcessor.scheduleProcessingOfExpiredRequests(member)

                mgmRegistrationOutputPublisher.createRecords(mgmInfo)
            } catch (ex: MGMRegistrationContextValidationException) {
                throw InvalidMembershipRegistrationException(ex.reason, ex)
            } catch (ex: MGMRegistrationMemberInfoHandlingException) {
                throw InvalidMembershipRegistrationException(ex.reason, ex)
            } catch (ex: MGMRegistrationGroupPolicyHandlingException) {
                throw InvalidMembershipRegistrationException(ex.reason, ex)
            } catch (ex: MGMRegistrationOutputPublisherException) {
                throw NotReadyMembershipRegistrationException(ex.reason, ex)
            } catch (ex: InvalidMembershipRegistrationException) {
                throw ex
            } catch (ex: NotReadyMembershipRegistrationException) {
                throw ex
            } catch (e: Exception) {
                throw NotReadyMembershipRegistrationException("Registration failed. Reason: ${e.message}", e)
            }
        }
    }

    /**
     * Calculates the serial based on latest member as accurate as possible, so
     * we compare the serial from the member info with the serial from the request.
     */
    private fun calculateSerial(serialFromInfo: Long, serialFromRequest: Long): Long {
        val futureSerialBasedOnInfo = serialFromInfo + 1
        // Need to add 2 here as the registration request is persisted with the serial number of the previous member
        // info (or 0 if it was first time registration).
        val futureSerialBasedOnRequest = serialFromRequest + 2
        return if (futureSerialBasedOnInfo >= futureSerialBasedOnRequest) {
            futureSerialBasedOnInfo
        } else {
            futureSerialBasedOnRequest
        }
    }

    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> handleStartEvent(coordinator)
            is StopEvent -> handleStopEvent(coordinator)
            is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
        }
    }

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        componentHandle?.close()
        componentHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
                LifecycleCoordinatorName.forComponent<ExpirationProcessor>(),
            )
        )
    }

    private fun handleStopEvent(coordinator: LifecycleCoordinator) {
        deactivate(coordinator)
        componentHandle?.close()
        componentHandle = null
    }

    private fun handleRegistrationChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        when (event.status) {
            LifecycleStatus.UP -> {
                activate(coordinator)
            }

            else -> {
                deactivate(coordinator)
            }
        }
    }
}
