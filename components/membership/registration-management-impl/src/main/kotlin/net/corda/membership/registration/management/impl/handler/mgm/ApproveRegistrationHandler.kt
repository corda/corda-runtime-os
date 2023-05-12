package net.corda.membership.registration.management.impl.handler.mgm

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.membership.actions.request.DistributeMemberInfo
import net.corda.data.membership.actions.request.MembershipActionsRequest
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.membership.db.lib.AddNotaryToGroupParametersService
import net.corda.membership.db.lib.ApproveMemberAndRegistrationRequestService
import net.corda.membership.groupparams.writer.service.GroupParametersWriterService
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.management.impl.DbTransactionFactory
import net.corda.membership.registration.management.impl.handler.MemberTypeChecker
import net.corda.membership.registration.management.impl.handler.MissingRegistrationStateException
import net.corda.membership.registration.management.impl.handler.RegistrationHandler
import net.corda.membership.registration.management.impl.handler.RegistrationHandlerResult
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC
import net.corda.schema.Schemas.Membership.MEMBER_LIST_TOPIC
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class ApproveRegistrationHandler(
    private val transactionFactory: DbTransactionFactory,
    clock: Clock,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val memberTypeChecker: MemberTypeChecker,
    private val groupReaderProvider: MembershipGroupReaderProvider,
    private val groupParametersFactory: GroupParametersFactory,
    private val groupParametersWriterService: GroupParametersWriterService,
    private val memberInfoFactory: MemberInfoFactory,
    keyEncodingService: KeyEncodingService,
    private val p2pRecordsFactory: P2pRecordsFactory = P2pRecordsFactory(
        cordaAvroSerializationFactory,
        clock,
    ),
) : RegistrationHandler<ApproveRegistration> {
    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val commandType = ApproveRegistration::class.java

    private val approver = ApproveMemberAndRegistrationRequestService(
        clock,
        cordaAvroSerializationFactory,
    )
    private val addNotary = AddNotaryToGroupParametersService(
        clock,
        memberInfoFactory,
        cordaAvroSerializationFactory,
        keyEncodingService,
    )

    override fun invoke(
        state: RegistrationState?,
        key: String,
        command: ApproveRegistration,
    ): RegistrationHandlerResult {
        if (state == null) throw MissingRegistrationStateException
        // Update the state of the request and member
        val approvedBy = state.mgm
        val approvedMember = state.registeringMember
        val registrationId = state.registrationId
        val messages = try {
            val mgm = memberTypeChecker.getMgmMemberInfo(approvedBy.toCorda())
                ?: throw CordaRuntimeException(
                    "Could not approve registration request: '$registrationId' - member ${approvedBy.x500Name} is not an MGM.",
                )
            if (memberTypeChecker.isMgm(approvedMember)) {
                throw CordaRuntimeException(
                    "The registration request: '$registrationId' cannot be approved by ${approvedMember.x500Name} as it is an MGM.",
                )
            }
            transactionFactory.transaction(approvedBy) { em ->
                val persistentMemberInfo = approver.update(
                    em,
                    approvedMember,
                    approvedBy,
                    registrationId,
                )
                val memberInfo = memberInfoFactory.create(persistentMemberInfo)

                // If approved member has notary role set, add notary to MGM's view of the group parameters.
                // Otherwise, retrieve epoch of current group parameters from the group reader.
                val epoch = if (memberInfo.notaryDetails != null) {
                    val persistedGroupParameters = addNotary.add(
                        em,
                        persistentMemberInfo,
                    )
                    val groupParameters = groupParametersFactory.create(persistedGroupParameters)
                    groupParametersWriterService.put(approvedBy.toCorda(), groupParameters)
                    groupParameters.epoch
                } else {
                    val reader = groupReaderProvider.getGroupReader(approvedBy.toCorda())
                    reader.groupParameters?.epoch
                } ?: throw CordaRuntimeException("Failed to get epoch of persisted group parameters.")
                val distributionAction = Record(
                    MEMBERSHIP_ACTIONS_TOPIC,
                    "${approvedMember.x500Name}-${approvedMember.groupId}",
                    MembershipActionsRequest(DistributeMemberInfo(mgm.holdingIdentity.toAvro(), approvedMember, epoch, memberInfo.serial)),
                )

                // Push member to member list kafka topic
                val memberRecord = Record(
                    topic = MEMBER_LIST_TOPIC,
                    key = "${approvedBy.toCorda().shortHash}-${approvedMember.toCorda().shortHash}",
                    value = persistentMemberInfo,
                )

                val persistApproveMessage = p2pRecordsFactory.createAuthenticatedMessageRecord(
                    source = approvedBy,
                    destination = approvedMember,
                    content = SetOwnRegistrationStatus(
                        registrationId,
                        RegistrationStatus.APPROVED,
                    ),
                    filter = MembershipStatusFilter.ACTIVE_OR_SUSPENDED,
                )

                listOf(memberRecord, persistApproveMessage, distributionAction)
            }
        } catch (e: Exception) {
            logger.warn("Could not approve registration request: '$registrationId'", e)
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
            RegistrationState(registrationId, approvedMember, approvedBy),
            messages,
        )
    }
}
