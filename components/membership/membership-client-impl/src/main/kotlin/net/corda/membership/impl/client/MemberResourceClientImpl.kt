package net.corda.membership.impl.client

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.core.ShortHash
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedData
import net.corda.data.membership.async.request.MembershipAsyncRequest
import net.corda.data.membership.async.request.RegistrationAsyncRequest
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.data.membership.common.v2.RegistrationStatus
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
import net.corda.membership.client.CouldNotFindEntityException
import net.corda.membership.client.Entity
import net.corda.membership.client.MemberResourceClient
import net.corda.membership.client.RegistrationProgressNotFoundException
import net.corda.membership.client.ServiceNotReadyException
import net.corda.membership.client.dto.MemberInfoSubmittedDto
import net.corda.membership.client.dto.RegistrationRequestProgressDto
import net.corda.membership.client.dto.RegistrationRequestStatusDto
import net.corda.membership.client.dto.RegistrationStatusDto
import net.corda.membership.client.dto.SubmittedRegistrationStatus
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.deserializeContext
import net.corda.membership.lib.registration.PRE_AUTH_TOKEN
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.lib.toWire
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.MEMBERSHIP_ASYNC_REQUEST_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.utilities.time.UTCClock
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

        const val PUBLISHER_NAME = "MemberOpsClient.publisher"

        // for watching the config changes
        const val CONFIG_HANDLE_NAME = "MemberOpsClient.configHandle"

        // for checking the components' health
        const val COMPONENT_HANDLE_NAME = "MemberOpsClient.componentHandle"

        private val clock = UTCClock()

        private val REGISTRATION_CONTEXT_KEYS = setOf(PRE_AUTH_TOKEN)
    }

    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroSerializer { logger.error("Failed to serialize key value pair list.") }

    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java
        )

    private interface InnerMemberOpsClient {
        fun startRegistration(
            holdingIdentityShortHash: ShortHash,
            registrationContext: Map<String, String>,
        ): RegistrationRequestProgressDto

        fun checkRegistrationProgress(holdingIdentityShortHash: ShortHash): List<RegistrationRequestStatusDto>

        fun checkSpecificRegistrationProgress(
            holdingIdentityShortHash: ShortHash,
            registrationRequestId: String
        ): RegistrationRequestStatusDto
    }

    private var impl: InnerMemberOpsClient = InactiveImpl

    private val coordinator = coordinatorFactory.createCoordinator<MemberResourceClient>(::processEvent)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun startRegistration(
        holdingIdentityShortHash: ShortHash,
        registrationContext: Map<String, String>
    ): RegistrationRequestProgressDto =
        impl.startRegistration(holdingIdentityShortHash, registrationContext)

    override fun checkSpecificRegistrationProgress(
        holdingIdentityShortHash: ShortHash,
        registrationRequestId: String
    ) = impl.checkSpecificRegistrationProgress(
        holdingIdentityShortHash,
        registrationRequestId
    )

    override fun checkRegistrationProgress(holdingIdentityShortHash: ShortHash) =
        impl.checkRegistrationProgress(holdingIdentityShortHash)

    private fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                coordinator.createManagedResource(COMPONENT_HANDLE_NAME) {
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                        )
                    )
                }
            }

            is StopEvent -> {
                coordinator.closeManagedResources(
                    setOf(
                        COMPONENT_HANDLE_NAME,
                        CONFIG_HANDLE_NAME,
                        PUBLISHER_NAME,
                    )
                )
                deactivate("Handling the stop event for component.")
            }

            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.UP -> {
                        coordinator.createManagedResource(CONFIG_HANDLE_NAME) {
                            configurationReadService.registerComponentForUpdates(
                                coordinator,
                                setOf(ConfigKeys.BOOT_CONFIG, MESSAGING_CONFIG)
                            )
                        }
                    }

                    else -> {
                        coordinator.closeManagedResources(
                            setOf(
                                PUBLISHER_NAME,
                                CONFIG_HANDLE_NAME,
                            )
                        )
                        deactivate("Service dependencies have changed status causing this component to deactivate.")
                    }
                }
            }

            is ConfigChangedEvent -> {
                val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
                val publisher = coordinator.createManagedResource(PUBLISHER_NAME) {
                    publisherFactory.createPublisher(
                        PublisherConfig(
                            ASYNC_CLIENT_ID
                        ),
                        messagingConfig,
                    )
                }
                impl = ActiveImpl(
                    publisher
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
        override fun startRegistration(
            holdingIdentityShortHash: ShortHash,
            registrationContext: Map<String, String>,
        ) =
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
        override fun startRegistration(
            holdingIdentityShortHash: ShortHash,
            registrationContext: Map<String, String>,
        ): RegistrationRequestProgressDto {
            val requestId = UUID.randomUUID().toString()
            val holdingIdentity =
                virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentityShortHash)
                    ?.holdingIdentity
                    ?: throw CouldNotFindEntityException(Entity.VIRTUAL_NODE, holdingIdentityShortHash)
            try {
                asyncPublisher.publish(
                    listOf(
                        Record(
                            MEMBERSHIP_ASYNC_REQUEST_TOPIC,
                            holdingIdentityShortHash.toString(),
                            MembershipAsyncRequest(
                                RegistrationAsyncRequest(
                                    holdingIdentityShortHash.toString(),
                                    requestId,
                                    registrationContext.toWire(),
                                ),
                                null,
                            )
                        )
                    )
                ).forEach {
                    it.join()
                }
            } catch (e: Exception) {
                logger.warn(
                    "Could not submit registration request for holding identity ID" +
                        " [$holdingIdentityShortHash].",
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
            val persistenceSuccess = try {
                val context = keyValuePairListSerializer.serialize(
                    registrationContext.filterNot {
                        it.key == SERIAL || REGISTRATION_CONTEXT_KEYS.contains(it.key)
                    }.toWire()
                )
                val additionalContext = keyValuePairListSerializer.serialize(
                    registrationContext.filter {
                        REGISTRATION_CONTEXT_KEYS.contains(it.key)
                    }.toWire()
                )
                val persistentOperation = membershipPersistenceClient.persistRegistrationRequest(
                    holdingIdentity,
                    RegistrationRequest(
                        RegistrationStatus.NEW,
                        requestId,
                        holdingIdentity,
                        SignedData(
                            ByteBuffer.wrap(context),
                            CryptoSignatureWithKey(
                                ByteBuffer.wrap(byteArrayOf()),
                                ByteBuffer.wrap(byteArrayOf())
                            ),
                            CryptoSignatureSpec("", null, null),
                        ),
                        SignedData(
                            ByteBuffer.wrap(additionalContext),
                            CryptoSignatureWithKey(
                                ByteBuffer.wrap(byteArrayOf()),
                                ByteBuffer.wrap(byteArrayOf())
                            ),
                            CryptoSignatureSpec("", null, null),
                        ),
                        registrationContext[SERIAL]?.toLong(),
                    )
                )
                when (val result = persistentOperation.execute()) {
                    is MembershipPersistenceResult.Failure -> {
                        logger.warn(
                            "Could not persist registration request for holding identity ID" +
                                " [$holdingIdentityShortHash]. ${result.errorMsg}",
                        )
                        asyncPublisher.publish(persistentOperation.createAsyncCommands().toList())
                        false
                    }
                    is MembershipPersistenceResult.Success -> true
                }
            } catch (e: Exception) {
                logger.warn(
                    "Could not persist registration request for holding identity ID" +
                        " [$holdingIdentityShortHash].",
                    e,
                )
                false
            }
            val reason = if (persistenceSuccess) {
                "Submitting registration request was successful."
            } else {
                "Submitting registration request was successful. " +
                    "The request will be available in the API eventually."
            }
            return RegistrationRequestProgressDto(
                requestId,
                sent,
                SubmittedRegistrationStatus.SUBMITTED,
                persistenceSuccess,
                reason,
                MemberInfoSubmittedDto(registrationContext),
            )
        }

        override fun checkRegistrationProgress(holdingIdentityShortHash: ShortHash): List<RegistrationRequestStatusDto> {
            val holdingIdentity = virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentityShortHash)
                ?: throw CouldNotFindEntityException(Entity.VIRTUAL_NODE, holdingIdentityShortHash)
            return try {
                membershipQueryClient.queryRegistrationRequests(
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
                ?: throw CouldNotFindEntityException(Entity.VIRTUAL_NODE, holdingIdentityShortHash)
            return try {
                val status =
                    membershipQueryClient.queryRegistrationRequest(
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
        private fun RegistrationRequestDetails.toDto(): RegistrationRequestStatusDto =
            RegistrationRequestStatusDto(
                this.registrationId,
                this.registrationSent,
                this.registrationLastModified,
                this.registrationStatus.toDto(),
                MemberInfoSubmittedDto(
                    mapOf(
                        "registrationProtocolVersion" to this.registrationProtocolVersion.toString(),
                    ) + this.memberProvidedContext.data.array().deserializeContext(keyValuePairListDeserializer)
                ),
                this.reason,
                this.serial,
            )
    }

    private fun RegistrationStatus.toDto(): RegistrationStatusDto {
        return when (this) {
            RegistrationStatus.NEW -> RegistrationStatusDto.NEW
            RegistrationStatus.SENT_TO_MGM -> RegistrationStatusDto.SENT_TO_MGM
            RegistrationStatus.RECEIVED_BY_MGM -> RegistrationStatusDto.RECEIVED_BY_MGM
            RegistrationStatus.STARTED_PROCESSING_BY_MGM -> RegistrationStatusDto.STARTED_PROCESSING_BY_MGM
            RegistrationStatus.PENDING_MEMBER_VERIFICATION -> RegistrationStatusDto.PENDING_MEMBER_VERIFICATION
            RegistrationStatus.PENDING_MANUAL_APPROVAL -> RegistrationStatusDto.PENDING_MANUAL_APPROVAL
            RegistrationStatus.PENDING_AUTO_APPROVAL -> RegistrationStatusDto.PENDING_AUTO_APPROVAL
            RegistrationStatus.DECLINED -> RegistrationStatusDto.DECLINED
            RegistrationStatus.INVALID -> RegistrationStatusDto.INVALID
            RegistrationStatus.FAILED -> RegistrationStatusDto.FAILED
            RegistrationStatus.APPROVED -> RegistrationStatusDto.APPROVED
        }
    }
}
