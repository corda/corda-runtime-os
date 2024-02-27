package net.corda.membership.impl.registration.dynamic.handler.member

import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.command.registration.member.PersistMemberRegistrationState
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.virtualnode.toCorda
import net.corda.data.membership.common.RegistrationStatus as RegistrationStatusV1
import net.corda.data.membership.common.v2.RegistrationStatus as RegistrationStatusV2
import net.corda.data.membership.p2p.SetOwnRegistrationStatus as SetOwnRegistrationStatusV1
import net.corda.data.membership.p2p.v2.SetOwnRegistrationStatus as SetOwnRegistrationStatusV2

internal class PersistMemberRegistrationStateHandler(
    private val membershipPersistenceClient: MembershipPersistenceClient,
) : RegistrationHandler<PersistMemberRegistrationState> {
    override fun invoke(
        state: RegistrationState?,
        key: String,
        command: PersistMemberRegistrationState,
    ): RegistrationHandlerResult {
        val member = command.member.toCorda()
        val request = command.request()
        val commands = membershipPersistenceClient.setRegistrationRequestStatus(
            member,
            request.registrationId,
            request.newStatus,
            request.reason
        ).createAsyncCommands()
        return RegistrationHandlerResult(
            null,
            commands.toList(),
        )
    }

    override val commandType = PersistMemberRegistrationState::class.java

    override fun getOwnerHoldingId(
        state: RegistrationState?,
        command: PersistMemberRegistrationState
    ): HoldingIdentity = command.member

    private fun PersistMemberRegistrationState.request(): SetOwnRegistrationStatusV2 {
        val request = this.setStatusRequest
        return when (request) {
            is SetOwnRegistrationStatusV2 -> request
            is SetOwnRegistrationStatusV1 -> SetOwnRegistrationStatusV2(
                request.registrationId,
                request.newStatus.toV2(),
                null
            )
            else -> throw IllegalArgumentException("Unknown request status '${request.javaClass}' received.")
        }
    }

    private fun RegistrationStatusV1.toV2(): RegistrationStatusV2 {
        return when (this) {
            RegistrationStatusV1.NEW -> RegistrationStatusV2.NEW
            RegistrationStatusV1.SENT_TO_MGM -> RegistrationStatusV2.SENT_TO_MGM
            RegistrationStatusV1.RECEIVED_BY_MGM -> RegistrationStatusV2.RECEIVED_BY_MGM
            RegistrationStatusV1.PENDING_MEMBER_VERIFICATION -> RegistrationStatusV2.PENDING_MEMBER_VERIFICATION
            RegistrationStatusV1.PENDING_MANUAL_APPROVAL -> RegistrationStatusV2.PENDING_MANUAL_APPROVAL
            RegistrationStatusV1.PENDING_AUTO_APPROVAL -> RegistrationStatusV2.PENDING_AUTO_APPROVAL
            RegistrationStatusV1.APPROVED -> RegistrationStatusV2.APPROVED
            RegistrationStatusV1.DECLINED -> RegistrationStatusV2.DECLINED
            RegistrationStatusV1.INVALID -> RegistrationStatusV2.INVALID
            RegistrationStatusV1.FAILED -> RegistrationStatusV2.FAILED
            else -> throw IllegalArgumentException("Unknown status '${this.name}' received.")
        }
    }
}
