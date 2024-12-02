package net.corda.membership.impl.registration.dynamic.handler.member

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.command.registration.member.ProcessMemberVerificationRequest
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.p2p.VerificationResponse
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.RegistrationLogger
import net.corda.membership.impl.registration.VerificationResponseKeys.FAILURE_REASONS
import net.corda.membership.impl.registration.VerificationResponseKeys.VERIFIED
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.lib.createMembershipAuthenticatedMessageRecord
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.utilities.time.Clock
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory

internal class ProcessMemberVerificationRequestHandler(
    clock: Clock,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val membershipPersistenceClient: MembershipPersistenceClient,
    private val memberTypeChecker: MemberTypeChecker,
    private val membershipP2PRecordsFactory: P2pRecordsFactory = P2pRecordsFactory(
        clock,
        cordaAvroSerializationFactory,
    ),
) : RegistrationHandler<ProcessMemberVerificationRequest> {
    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val commandType = ProcessMemberVerificationRequest::class.java

    override fun invoke(state: RegistrationState?, key: String, command: ProcessMemberVerificationRequest): RegistrationHandlerResult {
        val member = command.destination
        val mgm = command.source
        val registrationId = command.verificationRequest.registrationId
        val registrationLogger = RegistrationLogger(logger)
            .setRegistrationId(registrationId)
            .setMember(member)
            .setMgm(mgm)
        registrationLogger.info("Processing member verification request.")

        val reasons = mutableListOf<String>()
        if (memberTypeChecker.isMgm(member)) {
            reasons += "${member.x500Name} is an MGM and can not register"
        }

        val payload = reasons.map { KeyValuePair(FAILURE_REASONS, it) } + if (reasons.isEmpty()) {
            KeyValuePair(VERIFIED, true.toString())
        } else {
            registrationLogger.warn("Failed to verify request. $reasons")
            KeyValuePair(VERIFIED, false.toString())
        }

        val commands = membershipPersistenceClient.setRegistrationRequestStatus(
            member.toCorda(),
            registrationId,
            RegistrationStatus.PENDING_MEMBER_VERIFICATION,
        ).createAsyncCommands()

        return RegistrationHandlerResult(
            null,
            listOf(
                membershipP2PRecordsFactory.createMembershipAuthenticatedMessageRecord(
                    member,
                    mgm,
                    VerificationResponse(
                        registrationId,
                        KeyValuePairList(payload)
                    ),
                )
            ) + commands
        )
    }

    override fun getOwnerHoldingId(
        state: RegistrationState?,
        command: ProcessMemberVerificationRequest
    ): HoldingIdentity = command.destination
}
