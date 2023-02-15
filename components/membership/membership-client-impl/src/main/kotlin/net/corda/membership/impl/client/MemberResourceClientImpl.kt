package net.corda.membership.impl.client

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.async.request.MembershipAsyncRequest
import net.corda.data.membership.async.request.RegistrationAction
import net.corda.data.membership.async.request.RegistrationAsyncRequest
import net.corda.data.membership.common.RegistrationStatus
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.client.CouldNotFindMemberException
import net.corda.membership.client.MemberResourceClient
import net.corda.membership.client.RegistrationProgressNotFoundException
import net.corda.membership.client.ServiceNotReadyException
import net.corda.membership.client.dto.MemberInfoSubmittedDto
import net.corda.membership.client.dto.MemberRegistrationRequestDto
import net.corda.membership.client.dto.RegistrationRequestProgressDto
import net.corda.membership.client.dto.RegistrationRequestStatusDto
import net.corda.membership.client.dto.RegistrationStatusDto
import net.corda.membership.client.dto.SubmittedRegistrationStatus
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.lib.registration.RegistrationRequestStatus
import net.corda.membership.lib.toWire
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.Companion.MEMBERSHIP_ASYNC_REQUEST_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.utilities.time.UTCClock
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.UUID

@Component(service = [MemberResourceClient::class])
@Suppress("LongParameterList")
class MemberResourceClientImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    val publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationReadService::class)
    val configurationReadService: ConfigurationReadService,
    @Reference(service = MembershipPersistenceClient::class)
    private val membershipPersistenceClient: MembershipPersistenceClient,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = MembershipQueryClient::class)
    private val membershipQueryClient: MembershipQueryClient,
    @Reference(service = CordaAvroSerializationFactory::class)
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) : MemberResourceClient {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val ERROR_MSG = "Service is in an incorrect state for calling."

        const val ASYNC_CLIENT_ID = "membership.ops.async"

        private val clock = UTCClock()
    }

    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroSerializer { logger.error("Failed to serialize key value pair list.") }

    private interface InnerMemberOpsClient {
        fun startRegistration(memberRegistrationRequest: MemberRegistrationRequestDto): RegistrationRequestProgressDto

        fun checkRegistrationProgress(holdingIdentityShortHash: ShortHash): List<RegistrationRequestStatusDto>

        fun checkSpecificRegistrationProgress(
            holdingIdentityShortHash: ShortHash,
            registrationRequestId: String
        ): RegistrationRequestStatusDto
    }

    private var impl: InnerMemberOpsClient = InactiveImpl

    // for watching the config changes
    private var configHandle: AutoCloseable? = null

    // for checking the components' health
    private var componentHandle: AutoCloseable? = null

    private val coordinator = coordinatorFactory.createCoordinator<MemberResourceClient>(::processEvent)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun startRegistration(memberRegistrationRequest: MemberRegistrationRequestDto) =
        impl.startRegistration(memberRegistrationRequest)

    override fun checkSpecificRegistrationProgress(
        holdingIdentityShortHash: ShortHash,
        registrationRequestId: String
    ) = impl.checkSpecificRegistrationProgress(
        holdingIdentityShortHash, registrationRequestId
    )

    override fun checkRegistrationProgress(holdingIdentityShortHash: ShortHash) =
        impl.checkRegistrationProgress(holdingIdentityShortHash)

    private fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                componentHandle?.close()
                componentHandle = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                    )
                )
            }

            is StopEvent -> {
                componentHandle?.close()
                configHandle?.close()
                deactivate("Handling the stop event for component.")
            }

            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.UP -> {
                        configHandle?.close()
                        configHandle = configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(ConfigKeys.BOOT_CONFIG, MESSAGING_CONFIG)
                        )
                    }

                    else -> {
                        configHandle?.close()
                        deactivate("Service dependencies have changed status causing this component to deactivate.")
                    }
                }
            }

            is ConfigChangedEvent -> {
                val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
                impl = ActiveImpl(
                    publisherFactory.createPublisher(
                        PublisherConfig(
                            ASYNC_CLIENT_ID
                        ),
                        messagingConfig,
                    )
                )
                coordinator.updateStatus(LifecycleStatus.UP, "Dependencies are UP and configuration received.")
            }
        }
    }

    private fun deactivate(reason: String) {
        coordinator.updateStatus(LifecycleStatus.DOWN, reason)
        impl = InactiveImpl
    }

    private object InactiveImpl : InnerMemberOpsClient {
        override fun startRegistration(memberRegistrationRequest: MemberRegistrationRequestDto) =
            throw IllegalStateException(ERROR_MSG)

        override fun checkRegistrationProgress(holdingIdentityShortHash: ShortHash) =
            throw IllegalStateException(ERROR_MSG)

        override fun checkSpecificRegistrationProgress(
            holdingIdentityShortHash: ShortHash,
            registrationRequestId: String,
        ) =
            throw IllegalStateException(ERROR_MSG)
    }

    private inner class ActiveImpl(
        private val asyncPublisher: Publisher,
    ) : InnerMemberOpsClient {
        override fun startRegistration(memberRegistrationRequest: MemberRegistrationRequestDto): RegistrationRequestProgressDto {
            val requestId = UUID.randomUUID().toString()
            val holdingIdentity =
                virtualNodeInfoReadService.getByHoldingIdentityShortHash(memberRegistrationRequest.holdingIdentityShortHash)
                    ?.holdingIdentity
                    ?: throw CouldNotFindMemberException(memberRegistrationRequest.holdingIdentityShortHash)
            try {
                asyncPublisher.publish(
                    listOf(
                        Record(
                            MEMBERSHIP_ASYNC_REQUEST_TOPIC,
                            requestId,
                            MembershipAsyncRequest(
                                RegistrationAsyncRequest(
                                    memberRegistrationRequest.holdingIdentityShortHash.toString(),
                                    requestId,
                                    RegistrationAction.valueOf(memberRegistrationRequest.action.name),
                                    memberRegistrationRequest.context.toWire()
                                )
                            )
                        )
                    )
                ).forEach {
                    it.join()
                }
            } catch (e: Exception) {
                logger.warn(
                    "Could not submit registration request for holding identity ID" +
                        " [${memberRegistrationRequest.holdingIdentityShortHash}].",
                    e
                )
                val cause = e.cause ?: e
                return RegistrationRequestProgressDto(
                    requestId,
                    null,
                    SubmittedRegistrationStatus.NOT_SUBMITTED,
                    false,
                    cause.message ?: "No cause was provided for failure.",
                    MemberInfoSubmittedDto(emptyMap())
                )
            }
            val sent = clock.instant()
            try {
                val context = keyValuePairListSerializer.serialize(
                    memberRegistrationRequest.context.toWire()
                )
                membershipPersistenceClient.persistRegistrationRequest(
                    holdingIdentity,
                    RegistrationRequest(
                        RegistrationStatus.NEW,
                        requestId,
                        holdingIdentity,
                        ByteBuffer.wrap(context),
                        CryptoSignatureWithKey(
                            ByteBuffer.wrap(byteArrayOf()),
                            ByteBuffer.wrap(byteArrayOf()),
                            KeyValuePairList(emptyList())
                        ),
                        true
                    )
                ).getOrThrow()
                return RegistrationRequestProgressDto(
                    requestId,
                    sent,
                    SubmittedRegistrationStatus.SUBMITTED,
                    true,
                    "Submitting registration request was successful.",
                    MemberInfoSubmittedDto(memberRegistrationRequest.context)
                )
            } catch (e: Exception) {
                logger.warn(
                    "Could not persist registration request for holding identity ID" +
                        " [${memberRegistrationRequest.holdingIdentityShortHash}].",
                    e
                )
                return RegistrationRequestProgressDto(
                    requestId,
                    sent,
                    SubmittedRegistrationStatus.SUBMITTED,
                    false,
                    "Submitting registration request was successful. " +
                        "The request will be available in the API eventually.",
                    MemberInfoSubmittedDto(emptyMap())
                )
            }
        }

        override fun checkRegistrationProgress(holdingIdentityShortHash: ShortHash): List<RegistrationRequestStatusDto> {
            val holdingIdentity = virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentityShortHash)
                ?: throw CouldNotFindMemberException(holdingIdentityShortHash)
            return try {
                membershipQueryClient.queryRegistrationRequestsStatus(
                    holdingIdentity.holdingIdentity
                ).getOrThrow().map {
                    it.toDto()
                }
            } catch (e: MembershipQueryResult.QueryException) {
                throw ServiceNotReadyException(e)
            }
        }

        override fun checkSpecificRegistrationProgress(
            holdingIdentityShortHash: ShortHash,
            registrationRequestId: String,
        ): RegistrationRequestStatusDto {
            val holdingIdentity = virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentityShortHash)
                ?: throw CouldNotFindMemberException(holdingIdentityShortHash)
            return try {
                val status =
                    membershipQueryClient.queryRegistrationRequestStatus(
                        holdingIdentity.holdingIdentity,
                        registrationRequestId,
                    ).getOrThrow() ?: throw RegistrationProgressNotFoundException(
                        "There is no request with '$registrationRequestId' id in '$holdingIdentityShortHash'."
                    )
                status.toDto()
            } catch (e: MembershipQueryResult.QueryException) {
                throw ServiceNotReadyException(e)
            }
        }

        @Suppress("SpreadOperator")
        private fun RegistrationRequestStatus.toDto(): RegistrationRequestStatusDto =
            RegistrationRequestStatusDto(
                this.registrationId,
                this.registrationSent,
                this.registrationLastModified,
                this.status.toDto(),
                MemberInfoSubmittedDto(
                    mapOf(
                        "registrationProtocolVersion" to this.protocolVersion.toString(),
                        *this.memberContext.items.map { it.key to it.value }.toTypedArray(),
                    )
                )
            )
    }

    private fun RegistrationStatus.toDto(): RegistrationStatusDto {
        return when (this) {
            RegistrationStatus.NEW -> RegistrationStatusDto.NEW
            RegistrationStatus.SENT_TO_MGM -> RegistrationStatusDto.SENT_TO_MGM
            RegistrationStatus.RECEIVED_BY_MGM -> RegistrationStatusDto.RECEIVED_BY_MGM
            RegistrationStatus.PENDING_MEMBER_VERIFICATION -> RegistrationStatusDto.PENDING_MEMBER_VERIFICATION
            RegistrationStatus.PENDING_APPROVAL_FLOW -> RegistrationStatusDto.PENDING_APPROVAL_FLOW
            RegistrationStatus.PENDING_MANUAL_APPROVAL -> RegistrationStatusDto.PENDING_MANUAL_APPROVAL
            RegistrationStatus.PENDING_AUTO_APPROVAL -> RegistrationStatusDto.PENDING_AUTO_APPROVAL
            RegistrationStatus.DECLINED -> RegistrationStatusDto.DECLINED
            RegistrationStatus.INVALID -> RegistrationStatusDto.INVALID
            RegistrationStatus.APPROVED -> RegistrationStatusDto.APPROVED
        }
    }
}
