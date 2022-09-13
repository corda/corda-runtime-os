package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.command.registration.mgm.VerifyMember
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.VerificationRequest
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.toCorda

internal class VerifyMemberHandler(
    clock: Clock,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val membershipPersistenceClient: MembershipPersistenceClient,
    private val memberTypeChecker: MemberTypeChecker,
    private val p2pRecordsFactory: P2pRecordsFactory = P2pRecordsFactory(
        cordaAvroSerializationFactory,
        clock,
    )
) : RegistrationHandler<VerifyMember> {
    private companion object {
        val logger = contextLogger()
    }

    override val commandType = VerifyMember::class.java

    override fun invoke(state: RegistrationState?, key: String, command: VerifyMember): RegistrationHandlerResult {
        if (state == null) throw MissingRegistrationStateException
        val mgm = state.mgm
        val member = state.registeringMember
        val registrationId = state.registrationId
        val messages = try {
            if (!memberTypeChecker.isMgm(mgm)) {
                throw CordaRuntimeException("Could not verify $registrationId with ${mgm.x500Name} - Not an MGM.")
            }
            if (memberTypeChecker.isMgm(member)) {
                throw CordaRuntimeException("Could not verify $registrationId member ${member.x500Name} - Can not be an MGM.")
            }
            membershipPersistenceClient.setRegistrationRequestStatus(
                mgm.toCorda(),
                registrationId,
                RegistrationStatus.PENDING_MEMBER_VERIFICATION
            )
            listOf(
                p2pRecordsFactory.createAuthenticatedMessageRecord(
                    mgm,
                    member,
                    VerificationRequest(
                        registrationId,
                        KeyValuePairList(emptyList<KeyValuePair>())
                    )
                )
            )
        } catch (e: Exception) {
            logger.warn("Could not verify member fro registration $registrationId", e)
            listOf(
                Record(
                    Schemas.Membership.REGISTRATION_COMMAND_TOPIC,
                    key,
                    RegistrationCommand(
                        DeclineRegistration(e.message)
                    )
                ),
            )
        }
        return RegistrationHandlerResult(
            RegistrationState(registrationId, member, mgm),
            messages
        )
    }
}
