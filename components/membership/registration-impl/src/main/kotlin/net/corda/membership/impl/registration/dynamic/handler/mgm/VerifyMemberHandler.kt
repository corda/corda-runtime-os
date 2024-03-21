package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.command.registration.mgm.VerifyMember
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.p2p.VerificationRequest
import net.corda.data.membership.state.RegistrationState
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.impl.registration.RegistrationLogger
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.lib.createMembershipAuthenticatedMessageRecord
import net.corda.membership.lib.getTtlMinutes
import net.corda.membership.lib.registration.DECLINED_REASON_FOR_USER_INTERNAL_ERROR
import net.corda.membership.p2p.helpers.TtlIdsFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.messaging.api.records.Record
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.VERIFY_MEMBER_REQUEST
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class VerifyMemberHandler(
    clock: Clock,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val membershipPersistenceClient: MembershipPersistenceClient,
    private val memberTypeChecker: MemberTypeChecker,
    private val membershipConfig: SmartConfig,
    private val membershipP2PRecordsFactory: P2pRecordsFactory = P2pRecordsFactory(
        clock,
        cordaAvroSerializationFactory,
    ),
    private val ttlIdsFactory: TtlIdsFactory = TtlIdsFactory(),
) : RegistrationHandler<VerifyMember> {
    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val commandType = VerifyMember::class.java

    override fun invoke(state: RegistrationState?, key: String, command: VerifyMember): RegistrationHandlerResult {
        if (state == null) throw MissingRegistrationStateException
        val mgm = state.mgm
        val member = state.registeringMember
        val registrationId = state.registrationId

        val registrationLogger = RegistrationLogger(logger)
            .setRegistrationId(registrationId)
            .setMember(member)
            .setMgm(mgm)

        registrationLogger.info("Verifying member.")

        val messages = try {
            if (!memberTypeChecker.isMgm(mgm)) {
                registrationLogger.info("Could not verify registration request. Not an MGM.")
                throw CordaRuntimeException("Could not verify registration request: '$registrationId' with ${mgm.x500Name} - Not an MGM.")
            }
            if (memberTypeChecker.isMgm(member)) {
                registrationLogger.info("Could not verify registration request. Cannot be an MGM.")
                throw CordaRuntimeException(
                    "Could not verify registration request: '$registrationId' member ${member.x500Name} - Cannot be an MGM."
                )
            }
            val setRegistrationRequestStatusCommand = membershipPersistenceClient.setRegistrationRequestStatus(
                mgm.toCorda(),
                registrationId,
                RegistrationStatus.PENDING_MEMBER_VERIFICATION
            ).createAsyncCommands()
            setRegistrationRequestStatusCommand +
                membershipP2PRecordsFactory.createMembershipAuthenticatedMessageRecord(
                    mgm,
                    member,
                    VerificationRequest(
                        registrationId,
                        KeyValuePairList(emptyList<KeyValuePair>())
                    ),
                    membershipConfig.getTtlMinutes(VERIFY_MEMBER_REQUEST),
                    ttlIdsFactory.createId(key),
                    MembershipStatusFilter.PENDING,
                )
        } catch (e: Exception) {
            registrationLogger.warn("Member verification failed for registration request.", e)
            listOf(
                Record(
                    Schemas.Membership.REGISTRATION_COMMAND_TOPIC,
                    key,
                    RegistrationCommand(
                        DeclineRegistration(e.message, DECLINED_REASON_FOR_USER_INTERNAL_ERROR)
                    )
                ),
            )
        }
        return RegistrationHandlerResult(state, messages)
    }

    override fun getOwnerHoldingId(
        state: RegistrationState?,
        command: VerifyMember
    ) = state?.mgm
}
