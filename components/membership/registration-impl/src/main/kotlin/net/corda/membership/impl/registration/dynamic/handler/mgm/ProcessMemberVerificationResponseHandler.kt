package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.command.registration.mgm.ProcessMemberVerificationResponse
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.impl.registration.VerificationResponseKeys.FAILURE_REASONS
import net.corda.membership.impl.registration.VerificationResponseKeys.VERIFIED
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.lib.MemberInfoExtension.Companion.PRE_AUTH_TOKEN
import net.corda.membership.lib.approval.RegistrationRule
import net.corda.membership.lib.approval.RegistrationRulesEngine
import net.corda.membership.lib.toMap
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.p2p.helpers.P2pRecordsFactory.Companion.getTtlMinutes
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.Companion.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.UPDATE_TO_PENDING_AUTO_APPROVAL
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.membership.MemberContext
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
    private val p2pRecordsFactory: P2pRecordsFactory = P2pRecordsFactory(
        cordaAvroSerializationFactory,
        clock,
    ),
) : RegistrationHandler<ProcessMemberVerificationResponse> {
    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val commandType = ProcessMemberVerificationResponse::class.java

    override fun invoke(
        state: RegistrationState?,
        key: String,
        command: ProcessMemberVerificationResponse
    ): RegistrationHandlerResult {
        if (state == null) throw MissingRegistrationStateException
        val registrationId = state.registrationId
        val mgm = state.mgm
        val member = state.registeringMember
        val messages = try {
            val success = command.verificationResponse.payload.items.firstOrNull {
                it.key == VERIFIED
            }?.value?.toBooleanStrictOrNull() ?: false
            if (!success) {
                val reasons = command.verificationResponse.payload.items
                    .filter { it.key == FAILURE_REASONS }
                    .map { it.value }
                val message = "Could not verify registration request: '$registrationId' - $reasons"
                throw CordaRuntimeException(message)
            }
            if (memberTypeChecker.isMgm(member)) {
                throw CordaRuntimeException(
                    "Member ${member.x500Name} is an MGM and can not register."
                )
            }
            if (!memberTypeChecker.isMgm(mgm)) {
                throw CordaRuntimeException(
                    "Member ${mgm.x500Name} is not an MGM and can not process member's registration."
                )
            }

            val status = getNextRegistrationStatus(mgm.toCorda(), member.toCorda(), registrationId)
            membershipPersistenceClient.setRegistrationRequestStatus(
                mgm.toCorda(),
                registrationId,
                status
            )
            val persistStatusMessage = p2pRecordsFactory.createAuthenticatedMessageRecord(
                source = mgm,
                destination = member,
                content = SetOwnRegistrationStatus(
                    registrationId,
                    status
                ),
                minutesToWait = membershipConfig.getTtlMinutes(UPDATE_TO_PENDING_AUTO_APPROVAL)
            )
            val approveRecord = if (status == RegistrationStatus.PENDING_AUTO_APPROVAL) {
                Record(
                    REGISTRATION_COMMAND_TOPIC,
                    "$registrationId-${mgm.toCorda().shortHash}",
                    RegistrationCommand(ApproveRegistration())
                )
            } else null

            listOfNotNull(
                persistStatusMessage,
                approveRecord,
            )
        } catch (e: Exception) {
            logger.warn("Could not process member verification response for registration request: '$registrationId'", e)
            listOf(
                Record(
                    REGISTRATION_COMMAND_TOPIC,
                    key,
                    RegistrationCommand(
                        DeclineRegistration(e.message)
                    )
                ),
            )
        }
        return RegistrationHandlerResult(
            RegistrationState(registrationId, member, mgm),
            messages,
        )
    }

    private fun getNextRegistrationStatus(
        mgm: HoldingIdentity,
        member: HoldingIdentity,
        registrationId: String
    ): RegistrationStatus {
        val proposedMemberInfo = membershipQueryClient
            .queryRegistrationRequestStatus(mgm, registrationId)
            .getOrThrow()
            ?.memberContext
            ?.toMap()
            ?: throw CordaRuntimeException(
                "Could not read the proposed MemberInfo for registration request " +
                        "(ID=$registrationId) submitted by ${member.x500Name}."
            )

        val activeMemberInfo = membershipGroupReaderProvider
            .getGroupReader(mgm)
            .lookup(member.x500Name)
            ?.memberProvidedContext
            ?.toMap()

        val approvalRuleType = proposedMemberInfo[PRE_AUTH_TOKEN]?.let {
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

        proposedMemberInfo[PRE_AUTH_TOKEN]?.let {
            // Consume token after retrieving rules.
            membershipPersistenceClient.consumePreAuthToken(
                mgm,
                member.x500Name,
                parsePreAuthToken(it)
            ).getOrThrow()
        }

        return if (RegistrationRulesEngine.Impl(rules).requiresManualApproval(proposedMemberInfo, activeMemberInfo)) {
            RegistrationStatus.PENDING_MANUAL_APPROVAL
        } else {
            RegistrationStatus.PENDING_AUTO_APPROVAL
        }
    }

    private fun parsePreAuthToken(input: String): UUID {
        return try {
            UUID.fromString(input)
        } catch (e: IllegalArgumentException) {
            logger.warn(
                "Pre-auth token is incorrectly formatted and should have been handled when starting the " +
                        "registration.", e
            )
            throw InvalidPreAuthTokenException("Pre-auth token provided is not valid. A valid UUID is expected.")
        }
    }

    class InvalidPreAuthTokenException(msg: String) : CordaRuntimeException(msg)

    private fun MemberContext.toMap() = entries.associate { it.key to it.value }
}
