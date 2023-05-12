package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.membership.db.lib.PersistRegistrationRequestService
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.virtualnode.toCorda

internal class PersistRegistrationRequestHandler(
    persistenceHandlerServices: PersistenceHandlerServices,
) : BasePersistenceHandler<PersistRegistrationRequest, Unit>(persistenceHandlerServices) {

    private val persistenr = PersistRegistrationRequestService(clock)

    override fun invoke(context: MembershipRequestContext, request: PersistRegistrationRequest) {
        logger.info(
            "Persisting registration request with ID [${request.registrationRequest.registrationId}] to status ${request.status}.",
        )
        transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            persistenr.persist(
                em,
                RegistrationRequest(
                    request.status,
                    request.registrationRequest.registrationId,
                    request.registeringHoldingIdentity.toCorda(),
                    request.registrationRequest.memberContext,
                    request.registrationRequest.registrationContext,
                    request.registrationRequest.serial,
                ),
            )
        }
    }
}
