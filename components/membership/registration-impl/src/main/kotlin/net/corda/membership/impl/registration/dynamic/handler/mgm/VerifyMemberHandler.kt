package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.mgm.VerifyMember
import net.corda.data.membership.db.request.command.RegistrationStatus
import net.corda.data.membership.p2p.VerificationRequest
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.impl.registration.dynamic.handler.helpers.P2pRecordsFactory
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda

class VerifyMemberHandler(
    clock: Clock,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val membershipPersistenceClient: MembershipPersistenceClient,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
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
        if(state == null) throw MissingRegistrationStateException
        val mgm = state.mgm
        val member = state.registeringMember
        val registrationId = state.registrationId

        val mgmMemberInfo = getMGMMemberInfo(mgm.toCorda())
        membershipPersistenceClient.setRegistrationRequestStatus(
            mgmMemberInfo.holdingIdentity,
            registrationId,
            RegistrationStatus.PENDING_MEMBER_VERIFICATION
        )
        return RegistrationHandlerResult(
            RegistrationState(registrationId, member, mgm),
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
        )
    }

    private fun getMGMMemberInfo(mgm: HoldingIdentity): MemberInfo {
        val mgmMemberName = mgm.x500Name
        return membershipGroupReaderProvider.getGroupReader(mgm).lookup(mgmMemberName).apply {
            validateRegistrationRequest(this != null) {
                "Could not find MGM matching name: [$mgmMemberName]"
            }
            validateRegistrationRequest(this!!.isMgm) {
                "Registration request is targeted at non-MGM holding identity."
            }
        }!!
    }

    private class InvalidRegistrationRequestException(reason: String) : CordaRuntimeException(reason)

    private fun validateRegistrationRequest(condition: Boolean, errorMsg: () -> String) {
        if (!condition) {
            with(errorMsg.invoke()) {
                logger.error(this)
                throw InvalidRegistrationRequestException(this)
            }
        }
    }

}