package net.corda.membership.registration.management.impl.handler.member

import net.corda.data.membership.command.registration.member.PersistMemberRegistrationState
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.db.lib.UpdateRegistrationRequestStatusService
import net.corda.membership.registration.management.impl.handler.RegistrationHandler
import net.corda.membership.registration.management.impl.handler.RegistrationHandlerResult

internal class PersistMemberRegistrationStateHandler(
    private val updateRegistrationRequestStatusService: UpdateRegistrationRequestStatusService,
) : RegistrationHandler<PersistMemberRegistrationState> {
    override fun invoke(
        state: RegistrationState?,
        key: String,
        command: PersistMemberRegistrationState,
    ): RegistrationHandlerResult {
        val commands = updateRegistrationRequestStatusService.createCommand(
            command.member,
            command.setStatusRequest.registrationId,
            command.setStatusRequest.newStatus,
        )
        return RegistrationHandlerResult(
            null,
            commands.toList(),
        )
    }

    override val commandType = PersistMemberRegistrationState::class.java
}
