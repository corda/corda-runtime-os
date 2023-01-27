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
import net.corda.membership.lib.approval.RegistrationRule
import net.corda.membership.lib.approval.RegistrationRulesEngine
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
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda

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
        val logger = contextLogger()
    }

    override val commandType = ProcessMemberVerificationResponse::class.java

    override fun invoke(state: RegistrationState?, key: String, command: ProcessMemberVerificationResponse): RegistrationHandlerResult {
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
                throw CordaRuntimeException("Member ${member.x500Name} is an MGM and can not register.")
            }
            if (!memberTypeChecker.isMgm(mgm)) {
                throw CordaRuntimeException("Member ${mgm.x500Name} is not an MGM and can not process member's registration.")
            }

            val status = getApprovalType(mgm.toCorda(), member.toCorda())
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
            listOf(
                persistStatusMessage,
                Record(
                    REGISTRATION_COMMAND_TOPIC,
                    "$registrationId-${mgm.toCorda().shortHash}",
                    RegistrationCommand(ApproveRegistration())
                )
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

    private fun getApprovalType(mgm: HoldingIdentity, member: HoldingIdentity): RegistrationStatus {
        val proposedMemberInfo = with(membershipGroupReaderProvider.getGroupReader(mgm)) {
            lookup(member.x500Name) ?: throw CordaRuntimeException(
                "Could not read the proposed MemberInfo for registration request submitted by ${member.x500Name}."
            )
        }
        // TODO Get active MemberInfo from MembershipGroupReader after implementing re-registration.
        val activeMemberInfo = null

        val rules = membershipQueryClient.getApprovalRules(mgm, ApprovalRuleType.STANDARD).getOrThrow()
            .map { RegistrationRule.Impl(it.ruleRegex.toRegex()) }

        return with(RegistrationRulesEngine.Impl(rules)) {
            if (requiresManualApproval(proposedMemberInfo, activeMemberInfo)) {
                RegistrationStatus.PENDING_MANUAL_APPROVAL
            } else {
                RegistrationStatus.PENDING_AUTO_APPROVAL
            }
        }
    }
}
