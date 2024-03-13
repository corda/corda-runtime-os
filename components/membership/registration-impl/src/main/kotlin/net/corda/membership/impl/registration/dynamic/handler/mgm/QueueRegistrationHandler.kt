package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureVerificationService
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.CheckForPendingRegistration
import net.corda.data.membership.command.registration.mgm.QueueRegistration
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.membership.impl.registration.RegistrationLogger
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.lib.MemberInfoExtension.Companion.KEYS_PEM_SUFFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS
import net.corda.membership.lib.VersionedMessageBuilder
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.p2p.helpers.MembershipP2pRecordsFactory
import net.corda.membership.p2p.helpers.Verifier
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.messaging.api.records.Record
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class QueueRegistrationHandler(
    private val clock: Clock,
    private val membershipPersistenceClient: MembershipPersistenceClient,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    signatureVerificationService: SignatureVerificationService,
    private val keyEncodingService: KeyEncodingService,
    private val membershipP2PRecordsFactory: MembershipP2pRecordsFactory = MembershipP2pRecordsFactory(
        cordaAvroSerializationFactory,
        P2pRecordsFactory(clock),
    ),
    private val verifier: Verifier = Verifier(
        signatureVerificationService,
        keyEncodingService,
    ),
) : RegistrationHandler<QueueRegistration> {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
        val member = command.member.toCorda()
        val mgm = command.mgm.toCorda()
        val registrationLogger = RegistrationLogger(logger)
            .setRegistrationId(registrationId)
            .setMember(member)
            .setMgm(mgm)

        val outputCommand = try {
            if (command.numberOfRetriesSoFar < MAX_RETRIES) {
                queueRequest(key, command, registrationLogger)
            } else {
                registrationLogger.warn("Max re-tries exceeded. Registration is discarded.")
                emptyList()
            }
        } catch (ex: Exception) {
            registrationLogger.warn("Exception happened while queueing the request. Will re-try again.")
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
        key: String,
        command: QueueRegistration,
        registrationLogger: RegistrationLogger
    ): List<Record<*, *>> {
        val context = deserialize(command.memberRegistrationRequest.memberContext.data.array())

        // we should not queue request for re-try which failed on signature verification
        try {
            verifier.verify(
                context.getSessionKeys(),
                command.memberRegistrationRequest.memberContext.signature,
                command.memberRegistrationRequest.memberContext.signatureSpec,
                command.memberRegistrationRequest.memberContext.data.array(),
            )
        } catch (e: Exception) {
            registrationLogger.warn("Signature verification failed. Discarding the registration. Reason: ${e.message}")
            return emptyList()
        }

        val platformVersion = context.items.first { it.key == PLATFORM_VERSION }.value.toInt()
        // we need to create the status message based on which platform the member is on
        val statusUpdateMessage = VersionedMessageBuilder.retrieveRegistrationStatusMessage(
            platformVersion,
            command.memberRegistrationRequest.registrationId,
            RegistrationStatus.RECEIVED_BY_MGM.name,
            null
        )
        // if we are unable to create the status message, then we won't send anything
        val statusUpdateRecord = statusUpdateMessage?.let {
            membershipP2PRecordsFactory.createAuthenticatedMessageRecord(
                source = command.mgm,
                destination = command.member,
                content = statusUpdateMessage,
                minutesToWait = 5,
                filter = MembershipStatusFilter.PENDING
            )
        }

        registrationLogger.info("MGM queueing registration request.")
        membershipPersistenceClient.persistRegistrationRequest(
            command.mgm.toCorda(),
            command.toRegistrationRequest()
        ).getOrThrow()
        registrationLogger.info(
            "MGM successfully queued the registration request."
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

    private fun KeyValuePairList.getSessionKeys() =
        this.items.filter { items ->
            items.key.startsWith(SESSION_KEYS) && items.key.endsWith(KEYS_PEM_SUFFIX)
        }.map { sessionKeys ->
            keyEncodingService.decodePublicKey(sessionKeys.value)
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
