package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.UpdateMemberAndRegistrationRequestToApproved
import net.corda.data.membership.db.response.query.UpdateMemberAndRegistrationRequestResponse
import net.corda.membership.db.lib.ApproveMemberAndRegistrationRequestService
import net.corda.virtualnode.toCorda

internal class UpdateMemberAndRegistrationRequestToApprovedHandler(
    persistenceHandlerServices: PersistenceHandlerServices,
) : BasePersistenceHandler<
    UpdateMemberAndRegistrationRequestToApproved,
    UpdateMemberAndRegistrationRequestResponse,
    >(persistenceHandlerServices) {

    private val approver = ApproveMemberAndRegistrationRequestService(
        clock,
        cordaAvroSerializationFactory,
    )

    override fun invoke(
        context: MembershipRequestContext,
        request: UpdateMemberAndRegistrationRequestToApproved,
    ): UpdateMemberAndRegistrationRequestResponse {
        logger.info(
            "Update member and registration request with registration ID ${request.registrationId} to approved.",
        )
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val persistentMemberInfo = approver.update(
                em,
                request.member,
                context.holdingIdentity,
                request.registrationId,
            )
            UpdateMemberAndRegistrationRequestResponse(
                persistentMemberInfo,
            )
        }
    }
}
