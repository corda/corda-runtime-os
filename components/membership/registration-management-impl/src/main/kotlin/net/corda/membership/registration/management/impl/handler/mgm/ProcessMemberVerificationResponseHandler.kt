package net.corda.membership.registration.management.impl.handler.mgm

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.command.registration.mgm.ProcessMemberVerificationResponse
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.data.membership.preauth.PreAuthTokenStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.db.lib.ConsumePreAuthTokenService
import net.corda.membership.db.lib.QueryApprovalRulesService
import net.corda.membership.db.lib.QueryPreAuthTokenService
import net.corda.membership.db.lib.QueryRegistrationRequestService
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.approval.RegistrationRule
import net.corda.membership.lib.approval.RegistrationRulesEngine
import net.corda.membership.lib.registration.PRE_AUTH_TOKEN
import net.corda.membership.lib.toMap
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.p2p.helpers.P2pRecordsFactory.Companion.getTtlMinutes
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.management.impl.DbTransactionFactory
import net.corda.membership.registration.management.impl.handler.MemberTypeChecker
import net.corda.membership.registration.management.impl.handler.MissingRegistrationStateException
import net.corda.membership.registration.management.impl.handler.RegistrationHandler
import net.corda.membership.registration.management.impl.handler.RegistrationHandlerResult
import net.corda.membership.registration.management.impl.handler.VerificationResponseKeys.FAILURE_REASONS
import net.corda.membership.registration.management.impl.handler.VerificationResponseKeys.VERIFIED
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.UPDATE_TO_PENDING_AUTO_APPROVAL
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.membership.MemberContext
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class ProcessMemberVerificationResponseHandler(
    private val membershipPersistenceClient: MembershipPersistenceClient,
    clock: Clock,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val memberTypeChecker: MemberTypeChecker,
    private val membershipConfig: SmartConfig,
    private val transactionFactory: DbTransactionFactory,
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

    private val queryRegistrationRequestService = QueryRegistrationRequestService(cordaAvroSerializationFactory)
    private val queryPreAuthTokenService = QueryPreAuthTokenService()
    private val queryApprovalRulesService = QueryApprovalRulesService()
    private val consumer = ConsumePreAuthTokenService(clock)

    override fun invoke(
        state: RegistrationState?,
        key: String,
        command: ProcessMemberVerificationResponse,
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
                    "Member ${member.x500Name} is an MGM and can not register.",
                )
            }
            if (!memberTypeChecker.isMgm(mgm)) {
                throw CordaRuntimeException(
                    "Member ${mgm.x500Name} is not an MGM and can not process member's registration.",
                )
            }

            val status = getNextRegistrationStatus(transactionFactory, mgm, member, registrationId)
            val setRegistrationRequestStatusCommands = membershipPersistenceClient.setRegistrationRequestStatus(
                mgm.toCorda(),
                registrationId,
                status,
            ).createAsyncCommands()
            val persistStatusMessage = p2pRecordsFactory.createAuthenticatedMessageRecord(
                source = mgm,
                destination = member,
                content = SetOwnRegistrationStatus(
                    registrationId,
                    status,
                ),
                minutesToWait = membershipConfig.getTtlMinutes(UPDATE_TO_PENDING_AUTO_APPROVAL),
                filter = MembershipStatusFilter.PENDING,
            )
            val approveRecord = if (status == RegistrationStatus.PENDING_AUTO_APPROVAL) {
                Record(
                    REGISTRATION_COMMAND_TOPIC,
                    "$registrationId-${mgm.toCorda().shortHash}",
                    RegistrationCommand(ApproveRegistration()),
                )
            } else {
                null
            }

            listOfNotNull(
                persistStatusMessage,
                approveRecord,
            ) + setRegistrationRequestStatusCommands
        } catch (e: Exception) {
            logger.warn("Could not process member verification response for registration request: '$registrationId'", e)
            listOf(
                Record(
                    REGISTRATION_COMMAND_TOPIC,
                    key,
                    RegistrationCommand(
                        DeclineRegistration(e.message),
                    ),
                ),
            )
        }
        return RegistrationHandlerResult(
            RegistrationState(registrationId, member, mgm),
            messages,
        )
    }

    private fun getNextRegistrationStatus(
        transactionFactory: DbTransactionFactory,
        mgm: HoldingIdentity,
        member: HoldingIdentity,
        registrationId: String,
    ): RegistrationStatus {
        return transactionFactory.transaction(mgm) { em ->
            val memberName = member.toCorda().x500Name
            val registrationRequest = queryRegistrationRequestService.get(em, registrationId)
            val proposedMemberInfo = registrationRequest
                ?.memberProvidedContext
                ?.toMap()
                ?: throw CordaRuntimeException(
                    "Could not read the proposed MemberInfo for registration request " +
                        "(ID=$registrationId) submitted by ${member.x500Name}.",
                )
            val registrationContext = registrationRequest.registrationContext.toMap()
            val activeMemberInfo = membershipGroupReaderProvider
                .getGroupReader(mgm.toCorda())
                .lookup(memberName)
                ?.takeIf { it.status != MEMBER_STATUS_PENDING }
                ?.memberProvidedContext
                ?.toMap()

            val preAuthToken = registrationContext[PRE_AUTH_TOKEN]
            val approvalRuleType = preAuthToken?.let {
                val tokenExists = queryPreAuthTokenService.query(
                    em,
                    member.x500Name,
                    it,
                    listOf(PreAuthTokenStatus.AVAILABLE),
                ).isNotEmpty()
                if (tokenExists) {
                    ApprovalRuleType.PREAUTH
                } else {
                    throw InvalidPreAuthTokenException("Pre-auth token ID is invalid.")
                }
            } ?: ApprovalRuleType.STANDARD
            val rules = queryApprovalRulesService.get(
                em,
                approvalRuleType,
            ).map { RegistrationRule.Impl(it.ruleRegex.toRegex()) }

            preAuthToken?.let {
                // Consume token after retrieving rules.
                consumer.consume(
                    em,
                    memberName,
                    it,
                )
            }
            if (RegistrationRulesEngine.Impl(rules).requiresManualApproval(proposedMemberInfo, activeMemberInfo)) {
                RegistrationStatus.PENDING_MANUAL_APPROVAL
            } else {
                RegistrationStatus.PENDING_AUTO_APPROVAL
            }
        }
    }

    class InvalidPreAuthTokenException(msg: String) : CordaRuntimeException(msg)

    private fun MemberContext.toMap() = entries.associate { it.key to it.value }
}
