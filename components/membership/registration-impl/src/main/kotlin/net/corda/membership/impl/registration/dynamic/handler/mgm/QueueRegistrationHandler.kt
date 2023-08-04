package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.CheckForPendingRegistration
import net.corda.data.membership.command.registration.mgm.QueueRegistration
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.VersionedMessageBuilder
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.p2p.helpers.MessageIdsFactory
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class QueueRegistrationHandler(
    private val clock: Clock,
    private val membershipPersistenceClient: MembershipPersistenceClient,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val p2pRecordsFactory: P2pRecordsFactory = P2pRecordsFactory(
        cordaAvroSerializationFactory,
        clock,
    ),
) : RegistrationHandler<QueueRegistration> {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val idsFactory = MessageIdsFactory("QueueRegistrationHandler")
        const val MAX_RETRIES = 10
    }

    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java
        )
    }

    private fun deserialize(data: ByteArray): KeyValuePairList {
        return keyValuePairListDeserializer.deserialize(data) ?: throw CordaRuntimeException(
            "Failed to serialize key value pair list."
        )
    }

    override val commandType = QueueRegistration::class.java

    override fun getOwnerHoldingId(state: RegistrationState?, command: QueueRegistration) = state?.mgm

    override fun invoke(state: RegistrationState?, key: String, command: QueueRegistration): RegistrationHandlerResult {
        val registrationId = command.memberRegistrationRequest.registrationId
        val outputCommand = try {
            if (command.numberOfRetriesSoFar < MAX_RETRIES) {
                queueRequest(key, command, registrationId)
            } else {
                logger.warn(
                    "Max re-tries exceeded for registration with ID `$registrationId`." +
                            " Registration is discarded."
                )
                emptyList()
            }
        } catch (ex: Exception) {
            logger.warn("Exception happened while queueing the request with ID `$registrationId`. Will re-try again.")
            increaseNumberOfRetries(key, command)
        }
        return RegistrationHandlerResult(state, outputCommand)
    }

    private fun QueueRegistration.toRegistrationRequest(): RegistrationRequest {
        return RegistrationRequest(
            RegistrationStatus.RECEIVED_BY_MGM,
            memberRegistrationRequest.registrationId,
            member.toCorda(),
            memberRegistrationRequest.memberContext,
            memberRegistrationRequest.registrationContext,
            memberRegistrationRequest.serial,
        )
    }

    private fun queueRequest(
        key: String, command: QueueRegistration, registrationId: String
    ): List<Record<*, *>> {
        val context = deserialize(command.memberRegistrationRequest.memberContext.data.array())
        val platformVersion = context.items.first { it.key == PLATFORM_VERSION }.value.toInt()
        // we need to create the status message based on which platform the member is on
        val statusUpdateMessage = VersionedMessageBuilder.retrieveRegistrationStatusMessage(
            platformVersion,
            command.memberRegistrationRequest.registrationId,
            RegistrationStatus.RECEIVED_BY_MGM.name,
        )
        // if we are unable to create the status message, then we won't send anything
        val statusUpdateRecord = statusUpdateMessage?.let {
            p2pRecordsFactory.createAuthenticatedMessageRecord(
                source = command.mgm,
                destination = command.member,
                content = statusUpdateMessage,
                minutesToWait = 5,
                filter = MembershipStatusFilter.PENDING,
                id = idsFactory.createId(registrationId),
            )
        }

        logger.info(
            "MGM queueing registration request for ${command.member.x500Name} from group `${command.member.groupId}` " +
                    "with request ID `$registrationId`."
        )
        membershipPersistenceClient.persistRegistrationRequest(
            command.mgm.toCorda(),
            command.toRegistrationRequest()
        ).getOrThrow()
        logger.info(
            "MGM put registration request for ${command.member.x500Name} from group `${command.member.groupId}` " +
                    "with request ID `$registrationId` into the queue."
        )

        return listOfNotNull(
            statusUpdateRecord,
            Record(
                REGISTRATION_COMMAND_TOPIC,
                key,
                RegistrationCommand(CheckForPendingRegistration(command.mgm, command.member, 0))
            )
        )
    }

    private fun increaseNumberOfRetries(key: String, command: QueueRegistration) = listOf(
        Record(
            REGISTRATION_COMMAND_TOPIC,
            key,
            RegistrationCommand(
                QueueRegistration(
                    command.mgm,
                    command.member,
                    command.memberRegistrationRequest,
                    command.numberOfRetriesSoFar + 1
                )
            )
        )
    )
}