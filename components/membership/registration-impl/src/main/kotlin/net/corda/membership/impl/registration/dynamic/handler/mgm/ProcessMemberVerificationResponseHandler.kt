package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.command.registration.mgm.ProcessMemberVerificationResponse
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.impl.registration.RegistrationLogger
import net.corda.membership.impl.registration.VerificationResponseKeys.FAILURE_REASONS
import net.corda.membership.impl.registration.VerificationResponseKeys.VERIFIED
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.VersionedMessageBuilder.retrieveRegistrationStatusMessage
import net.corda.membership.lib.approval.RegistrationRule
import net.corda.membership.lib.approval.RegistrationRulesEngine
import net.corda.membership.lib.deserializeContext
import net.corda.membership.lib.registration.DECLINED_REASON_FOR_USER_INTERNAL_ERROR
import net.corda.membership.lib.registration.PRE_AUTH_TOKEN
import net.corda.membership.lib.toMap
import net.corda.membership.p2p.helpers.MembershipP2pRecordsFactory
import net.corda.membership.p2p.helpers.MembershipP2pRecordsFactory.Companion.getTtlMinutes
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.UPDATE_TO_PENDING_AUTO_APPROVAL
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.util.UUID

@Suppress("LongParameterList")
internal class ProcessMemberVerificationResponseHandler(
    private val membershipPersistenceClient: MembershipPersistenceClient,
    clock: Clock,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val memberTypeChecker: MemberTypeChecker,
    private val membershipConfig: SmartConfig,
    private val membershipQueryClient: MembershipQueryClient,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val membershipP2PRecordsFactory: MembershipP2pRecordsFactory = MembershipP2pRecordsFactory(
        cordaAvroSerializationFactory,
        P2pRecordsFactory(clock),
    ),
) : RegistrationHandler<ProcessMemberVerificationResponse> {
    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val deserializer: CordaAvroDeserializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java
        )

    override val commandType = ProcessMemberVerificationResponse::class.java

    override fun invoke(
        state: RegistrationState?,
        key: String,
        command: ProcessMemberVerificationResponse
    ): RegistrationHandlerResult {
        if (processingShouldBeSkipped(state)) {
            return RegistrationHandlerResult(state, emptyList(), skipped = true)
        }
        val registrationId = state!!.registrationId
        val mgm = state.mgm
        val member = state.registeringMember

        val registrationLogger = RegistrationLogger(logger)
            .setRegistrationId(registrationId)
            .setMember(member)
            .setMgm(member)

        registrationLogger.info("Processing member verification response.")

        val messages = try {
            val success = command.verificationResponse.payload.items.firstOrNull {
                it.key == VERIFIED
            }?.value?.toBooleanStrictOrNull() ?: false
            if (!success) {
                val reasons = command.verificationResponse.payload.items
                    .filter { it.key == FAILURE_REASONS }
                    .map { it.value }
                val message = "Could not verify registration request. $reasons"
                throw CordaRuntimeException(message)
            }
            if (memberTypeChecker.isMgm(member)) {
                throw CordaRuntimeException(
                    "Member is an MGM and cannot register."
                )
            }
            if (!memberTypeChecker.isMgm(mgm)) {
                throw CordaRuntimeException(
                    "Member is not an MGM and cannot process member's registration."
                )
            }

            val groupReader = membershipGroupReaderProvider.getGroupReader(mgm.toCorda())
            val status = getNextRegistrationStatus(mgm.toCorda(), member.toCorda(), registrationId, groupReader, registrationLogger)
            val pendingInfo = groupReader.lookup(member.toCorda().x500Name, MembershipStatusFilter.PENDING)
                ?: throw CordaRuntimeException("Could not find pending information for member.")
            val setRegistrationRequestStatusCommands = membershipPersistenceClient.setRegistrationRequestStatus(
                mgm.toCorda(),
                registrationId,
                status
            ).createAsyncCommands()
            val statusUpdateMessage = retrieveRegistrationStatusMessage(
                pendingInfo.platformVersion,
                registrationId,
                status.name,
                null
            )
            val persistStatusMessage = if (statusUpdateMessage != null) {
                membershipP2PRecordsFactory.createAuthenticatedMessageRecord(
                    source = mgm,
                    destination = member,
                    content = statusUpdateMessage,
                    minutesToWait = membershipConfig.getTtlMinutes(UPDATE_TO_PENDING_AUTO_APPROVAL),
                    filter = MembershipStatusFilter.PENDING,
                )
            } else { null }
            val approveRecord = if (status == RegistrationStatus.PENDING_AUTO_APPROVAL) {
                Record(
                    REGISTRATION_COMMAND_TOPIC,
                    key,
                    RegistrationCommand(ApproveRegistration())
                )
            } else {
                null
            }

            listOfNotNull(
                persistStatusMessage,
                approveRecord,
            ) + setRegistrationRequestStatusCommands
        } catch (e: Exception) {
            registrationLogger.warn("Could not process member verification response for registration request.", e)
            listOf(
                Record(
                    REGISTRATION_COMMAND_TOPIC,
                    key,
                    RegistrationCommand(
                        DeclineRegistration(e.message, DECLINED_REASON_FOR_USER_INTERNAL_ERROR)
                    )
                ),
            )
        }
        return RegistrationHandlerResult(state, messages)
    }

    private fun getNextRegistrationStatus(
        mgm: HoldingIdentity,
        member: HoldingIdentity,
        registrationId: String,
        groupReader: MembershipGroupReader,
        registrationLogger: RegistrationLogger
    ): RegistrationStatus {
        val registrationRequest = membershipQueryClient
            .queryRegistrationRequest(mgm, registrationId)
            .getOrThrow()
        val proposedMemberInfo = registrationRequest
            ?.memberProvidedContext?.data?.array()?.deserializeContext(deserializer)
            ?: throw CordaRuntimeException(
                "Could not read the proposed MemberInfo for registration request."
            )
        val registrationContext = registrationRequest.registrationContext.data.array().deserializeContext(deserializer)

        val activeMemberInfo = groupReader
            .lookup(member.x500Name)
            ?.takeIf { it.status != MEMBER_STATUS_PENDING }
            ?.memberProvidedContext
            ?.toMap()

        val preAuthToken = registrationContext[PRE_AUTH_TOKEN]

        val approvalRuleType = preAuthToken?.let {
            val tokenExists = membershipQueryClient.queryPreAuthTokens(
                mgm,
                member.x500Name,
                UUID.fromString(it),
                false
            ).getOrThrow().isNotEmpty()

            if (tokenExists) {
                ApprovalRuleType.PREAUTH
            } else {
                throw InvalidPreAuthTokenException("Pre-auth token ID is invalid.")
            }
        } ?: ApprovalRuleType.STANDARD

        val rules = membershipQueryClient
            .getApprovalRules(mgm, approvalRuleType)
            .getOrThrow()
            .map { RegistrationRule.Impl(it.ruleRegex.toRegex()) }

        preAuthToken?.let {
            // Consume token after retrieving rules.
            membershipPersistenceClient.consumePreAuthToken(
                mgm,
                member.x500Name,
                parsePreAuthToken(it, registrationLogger)
            ).getOrThrow()
        }

        return if (RegistrationRulesEngine.Impl(rules).requiresManualApproval(proposedMemberInfo, activeMemberInfo)) {
            RegistrationStatus.PENDING_MANUAL_APPROVAL
        } else {
            RegistrationStatus.PENDING_AUTO_APPROVAL
        }
    }

    override fun getOwnerHoldingId(
        state: RegistrationState?,
        command: ProcessMemberVerificationResponse
    ) = state?.mgm

    private fun parsePreAuthToken(input: String, registrationLogger: RegistrationLogger): UUID {
        return try {
            UUID.fromString(input)
        } catch (e: IllegalArgumentException) {
            registrationLogger.warn(
                "Pre-auth token is incorrectly formatted and should have been handled when starting the " +
                    "registration.",
                e
            )
            throw InvalidPreAuthTokenException("Pre-auth token provided is not valid. A valid UUID is expected.")
        }
    }

    private fun isCommandPreviouslyProcessed(state: RegistrationState): Boolean =
        state.previouslyCompletedCommands.map { it.command }.contains(commandType.simpleName)

    // Continue without processing this stage again if the state has been nullified or if the command has been executed previously.
    // This is to prevent multiple processing attempts in the case of replays at a p2p level.
    private fun processingShouldBeSkipped(state: RegistrationState?): Boolean = if (state == null) {
        logger.info(
            "${ProcessMemberVerificationResponse::class.java.simpleName} command ignored. " +
                "Registration state is null indicating that registration processing has completed."
        )
        true
    } else if (isCommandPreviouslyProcessed(state)) {
        logger.info(
            "${ProcessMemberVerificationResponse::class.java.simpleName} command ignored. " +
                "Command was processed already."
        )
        true
    } else {
        false
    }

    class InvalidPreAuthTokenException(msg: String) : CordaRuntimeException(msg)
}
