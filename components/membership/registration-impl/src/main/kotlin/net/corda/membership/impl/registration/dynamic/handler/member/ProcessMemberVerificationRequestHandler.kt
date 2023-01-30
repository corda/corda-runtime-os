package net.corda.membership.impl.registration.dynamic.handler.member

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.member.ProcessMemberVerificationRequest
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.VerificationResponse
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.VerificationResponseKeys.FAILURE_REASONS
import net.corda.membership.impl.registration.VerificationResponseKeys.VERIFIED
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.utilities.time.Clock
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory

internal class ProcessMemberVerificationRequestHandler(
    clock: Clock,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val membershipPersistenceClient: MembershipPersistenceClient,
    private val memberTypeChecker: MemberTypeChecker,
    private val p2pRecordsFactory: P2pRecordsFactory = P2pRecordsFactory(
        cordaAvroSerializationFactory,
        clock,
    )
) : RegistrationHandler<ProcessMemberVerificationRequest> {
    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val commandType = ProcessMemberVerificationRequest::class.java

    override fun invoke(state: RegistrationState?, key: String, command: ProcessMemberVerificationRequest): RegistrationHandlerResult {
        val member = command.destination
        val mgm = command.source
        val reasons = mutableListOf<String>()
        if (memberTypeChecker.isMgm(member)) {
            reasons += "${member.x500Name} is an MGM and can not register"
        }
        val payload = reasons.map { KeyValuePair(FAILURE_REASONS, it) } + if (reasons.isEmpty()) {
            KeyValuePair(VERIFIED, true.toString())
        } else {
            logger.warn("Failed to verify request: ${command.verificationRequest.registrationId} - $reasons")
            KeyValuePair(VERIFIED, false.toString())
        }

        val registrationId = command.verificationRequest.registrationId

        membershipPersistenceClient.setRegistrationRequestStatus(
            member.toCorda(),
            registrationId,
            RegistrationStatus.PENDING_MEMBER_VERIFICATION,
        )

        return RegistrationHandlerResult(
            null,
            listOf(
                p2pRecordsFactory.createAuthenticatedMessageRecord(
                    member,
                    mgm,
                    VerificationResponse(
                        registrationId,
                        KeyValuePairList(payload)
                    )
                )
            )
        )
    }
}
