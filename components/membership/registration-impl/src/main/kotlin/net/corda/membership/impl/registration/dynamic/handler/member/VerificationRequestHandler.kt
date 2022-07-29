package net.corda.membership.impl.registration.dynamic.handler.member

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.member.ProcessMemberVerificationRequest
import net.corda.data.membership.p2p.VerificationResponse
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.impl.registration.dynamic.handler.helpers.P2pRecordsFactory
import net.corda.utilities.time.Clock

internal class VerificationRequestHandler(
    clock: Clock,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val p2pRecordsFactory: P2pRecordsFactory = P2pRecordsFactory(
        cordaAvroSerializationFactory,
        clock,
    )
) : RegistrationHandler<ProcessMemberVerificationRequest> {
    override val commandType = ProcessMemberVerificationRequest::class.java

    override fun invoke(state: RegistrationState?, key: String, command: ProcessMemberVerificationRequest): RegistrationHandlerResult {
        val mgm = command.source
        val member = command.destination
        return RegistrationHandlerResult(
            null,
            listOf(
                p2pRecordsFactory.createAuthenticatedMessageRecord(
                    member,
                    mgm,
                    VerificationResponse(
                        command.verificationRequest.registrationId,
                        KeyValuePairList(emptyList<KeyValuePair>())
                    )
                )
            )
        )
    }
}