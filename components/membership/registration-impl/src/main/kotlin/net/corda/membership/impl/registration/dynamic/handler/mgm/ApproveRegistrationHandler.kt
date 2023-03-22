package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.command.registration.mgm.DistributeMembershipPackage
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.layeredpropertymap.toAvro
import net.corda.membership.groupparams.writer.service.GroupParametersWriterService
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.MEMBER_LIST_TOPIC
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
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
    private val groupParametersFactory: GroupParametersFactory,
    private val p2pRecordsFactory: P2pRecordsFactory = P2pRecordsFactory(
        cordaAvroSerializationFactory,
        clock,
    ),
) : RegistrationHandler<ApproveRegistration> {
    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val commandType = ApproveRegistration::class.java

    override fun invoke(state: RegistrationState?, key: String, command: ApproveRegistration): RegistrationHandlerResult {
        if (state == null) throw MissingRegistrationStateException
        // Update the state of the request and member
        val approvedBy = state.mgm
        val approvedMember = state.registeringMember
        val registrationId = state.registrationId
        val messages = try {
            val mgm = memberTypeChecker.getMgmMemberInfo(approvedBy.toCorda())
                ?: throw CordaRuntimeException(
                    "Could not approve registration request: '$registrationId' - member ${approvedBy.x500Name} is not an MGM."
                )
            if (memberTypeChecker.isMgm(approvedMember)) {
                throw CordaRuntimeException(
                    "The registration request: '$registrationId' cannot be approved by ${approvedMember.x500Name} as it is an MGM."
                )
            }

            val persistState = membershipPersistenceClient.setMemberAndRegistrationRequestAsApproved(
                viewOwningIdentity = approvedBy.toCorda(),
                approvedMember = approvedMember.toCorda(),
                registrationRequestId = registrationId,
            )
            val memberInfo = persistState.getOrThrow()

            // If approved member has notary role set, add notary to MGM's view of the group parameters.
            // Otherwise, retrieve epoch of current group parameters from the group reader.
            val epoch = if (memberInfo.notaryDetails != null) {
                val mgmHoldingIdentity = mgm.holdingIdentity
                val result = membershipPersistenceClient.addNotaryToGroupParameters(mgmHoldingIdentity, memberInfo)
                if (result is MembershipPersistenceResult.Failure) {
                    throw MembershipPersistenceException(
                        "Failed to update group parameters with notary information of" +
                                " '${memberInfo.name}', which has role set to 'notary'."
                    )
                }
                val persistedGroupParameters = groupParametersFactory.create(result.getOrThrow())
                groupParametersWriterService.put(mgmHoldingIdentity, persistedGroupParameters)
                persistedGroupParameters.epoch
            } else {
                val reader = groupReaderProvider.getGroupReader(approvedBy.toCorda())
                reader.groupParameters?.epoch
            } ?: throw CordaRuntimeException("Failed to get epoch of persisted group parameters.")

            val distributionCommand = Record(
                REGISTRATION_COMMAND_TOPIC,
                "$registrationId-${approvedBy.toCorda().shortHash}",
                RegistrationCommand(DistributeMembershipPackage(epoch)),
            )

            // Push member to member list kafka topic
            val persistentMemberInfo = PersistentMemberInfo.newBuilder()
                .setMemberContext(memberInfo.memberProvidedContext.toAvro())
                .setViewOwningMember(approvedBy)
                .setMgmContext(memberInfo.mgmProvidedContext.toAvro())
                .build()
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
                    RegistrationStatus.APPROVED
                )
            )

            listOf(memberRecord, persistApproveMessage, distributionCommand)
        } catch (e: Exception) {
            logger.warn("Could not approve registration request: '$registrationId'", e)
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
            RegistrationState(registrationId, approvedMember, approvedBy),
            messages
        )
    }
}
