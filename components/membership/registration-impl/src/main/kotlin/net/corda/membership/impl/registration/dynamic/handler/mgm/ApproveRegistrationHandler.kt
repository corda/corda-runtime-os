package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.membership.actions.request.DistributeMemberInfo
import net.corda.data.membership.actions.request.MembershipActionsRequest
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.CheckForPendingRegistration
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.membership.groupparams.writer.service.GroupParametersWriterService
import net.corda.membership.impl.registration.RegistrationLogger
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.VersionedMessageBuilder.retrieveRegistrationStatusMessage
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.registration.DECLINED_REASON_FOR_USER_INTERNAL_ERROR
import net.corda.membership.p2p.helpers.MembershipP2pRecordsFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.InvalidMembershipRegistrationException
import net.corda.messaging.api.records.Record
import net.corda.p2p.messaging.P2pRecordsFactory
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
    private val membershipPersistenceClient: MembershipPersistenceClient,
    clock: Clock,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val memberTypeChecker: MemberTypeChecker,
    private val groupReaderProvider: MembershipGroupReaderProvider,
    private val groupParametersWriterService: GroupParametersWriterService,
    private val memberInfoFactory: MemberInfoFactory,
    private val membershipP2PRecordsFactory: MembershipP2pRecordsFactory = MembershipP2pRecordsFactory(
        cordaAvroSerializationFactory,
        P2pRecordsFactory(clock),
    ),
) : RegistrationHandler<ApproveRegistration> {
    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val commandType = ApproveRegistration::class.java

    override fun getOwnerHoldingId(
        state: RegistrationState?,
        command: ApproveRegistration
    ) = state?.mgm

    override fun invoke(
        state: RegistrationState?,
        key: String,
        command: ApproveRegistration
    ): RegistrationHandlerResult {
        if (state == null) throw MissingRegistrationStateException
        // Update the state of the request and member
        val approvedBy = state.mgm
        val approvedMember = state.registeringMember
        val registrationId = state.registrationId
        val registrationLogger = RegistrationLogger(logger)
            .setRegistrationId(registrationId)
            .setMember(approvedMember)
            .setMgm(approvedBy)
        registrationLogger.info("Processing registration approval.")
        val messages = try {
            val mgm = memberTypeChecker.getMgmMemberInfo(approvedBy.toCorda())
                ?: throw CordaRuntimeException(
                    "Could not approve registration request. Member is not an MGM."
                )
            if (memberTypeChecker.isMgm(approvedMember)) {
                throw CordaRuntimeException(
                    "The registration request cannot be approved for member as it is an MGM."
                )
            }
            val groupParameters = groupReaderProvider.getGroupReader(approvedBy.toCorda()).groupParameters
                ?: throw CordaRuntimeException("Failed to retrieve persisted group parameters.")
            require(groupParameters.notaries.none { it.name.toString() == approvedMember.x500Name }) {
                throw InvalidMembershipRegistrationException(
                    "Registering member's name '${approvedMember.x500Name}' is already in use as a notary service name."
                )
            }
            val persistentMemberInfo = membershipPersistenceClient.setMemberAndRegistrationRequestAsApproved(
                viewOwningIdentity = approvedBy.toCorda(),
                approvedMember = approvedMember.toCorda(),
                registrationRequestId = registrationId,
            ).getOrThrow()

            val memberInfo = memberInfoFactory.createMemberInfo(persistentMemberInfo)

            // If approved member has notary role set, add notary to MGM's view of the group parameters.
            // Otherwise, retrieve epoch of current group parameters from the group reader.
            val epoch = if (memberInfo.notaryDetails != null) {
                val mgmHoldingIdentity = mgm.holdingIdentity
                val result = membershipPersistenceClient.addNotaryToGroupParameters(persistentMemberInfo)
                    .execute()
                if (result is MembershipPersistenceResult.Failure) {
                    throw MembershipPersistenceException(
                        "Failed to update group parameters with notary information of" +
                            " '${memberInfo.name}', which has role set to 'notary'."
                    )
                }
                val persistedGroupParameters = result.getOrThrow()
                groupParametersWriterService.put(mgmHoldingIdentity, persistedGroupParameters)
                persistedGroupParameters.epoch
            } else {
                groupParameters.epoch
            }

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

            val statusUpdateMessage = retrieveRegistrationStatusMessage(
                memberInfo.platformVersion,
                registrationId,
                RegistrationStatus.APPROVED.name,
                null
            )
            val persistApproveMessage = if (statusUpdateMessage != null) {
                membershipP2PRecordsFactory.createAuthenticatedMessageRecord(
                    source = approvedBy,
                    destination = approvedMember,
                    content = statusUpdateMessage,
                    filter = MembershipStatusFilter.ACTIVE_OR_SUSPENDED
                )
            } else { null }

            val commandToStartProcessingTheNextRequest = Record(
                topic = REGISTRATION_COMMAND_TOPIC,
                key = key,
                value = RegistrationCommand(CheckForPendingRegistration(approvedBy, approvedMember, 0))
            )

            listOfNotNull(memberRecord, persistApproveMessage, distributionAction, commandToStartProcessingTheNextRequest)
        } catch (e: Exception) {
            registrationLogger.warn("Could not approve registration request.", e)
            return RegistrationHandlerResult(
                state,
                listOf(
                    Record(
                        REGISTRATION_COMMAND_TOPIC,
                        key,
                        RegistrationCommand(DeclineRegistration(e.message, DECLINED_REASON_FOR_USER_INTERNAL_ERROR))
                    )
                )
            )
        }

        return RegistrationHandlerResult(
            null,
            messages
        )
    }
}
