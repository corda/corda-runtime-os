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
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda

internal class VerificationRequestHandler(
    clock: Clock,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val p2pRecordsFactory: P2pRecordsFactory = P2pRecordsFactory(
        cordaAvroSerializationFactory,
        clock,
    )
) : RegistrationHandler<ProcessMemberVerificationRequest> {

    private companion object {
        val logger = contextLogger()
    }

    override val commandType = ProcessMemberVerificationRequest::class.java

    override fun invoke(state: RegistrationState?, key: String, command: ProcessMemberVerificationRequest): RegistrationHandlerResult {
        val mgm = command.source
        val member = command.destination

        val mgmMemberInfo = getMGMMemberInfo(mgm.toCorda())
        val memberInfo = getMemberHoldingIdentityId(member.toCorda())

        return RegistrationHandlerResult(
            null,
            listOf(
                p2pRecordsFactory.createAuthenticatedMessageRecord(
                    memberInfo.holdingIdentity.toAvro(),
                    mgmMemberInfo.holdingIdentity.toAvro(),
                    VerificationResponse(
                        command.verificationRequest.registrationId,
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

    private fun getMemberHoldingIdentityId(member: HoldingIdentity): MemberInfo {

        return membershipGroupReaderProvider.getGroupReader(member).lookup(member.x500Name)?.apply {
            validateRegistrationRequest(!this.isMgm)
            {
                "Registration request is originated from a MGM holding identity."
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