package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.virtualnode.toCorda

class PersistRegistrationRequestHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistRegistrationRequest, Unit>(persistenceHandlerServices) {
    override fun invoke(context: MembershipRequestContext, request: PersistRegistrationRequest) {
        logger.info("Persisting registration request with ID [${request.registrationRequest.registrationId}].")
        transaction(context.holdingIdentity.toCorda().id) { em ->
            val now = clock.instant()
            em.merge(
                RegistrationRequestEntity(
                    request.registrationRequest.registrationId,
                    request.registeringHoldingIdentity.toCorda().id,
                    request.status.toString(),
                    now,
                    now,
                    request.registrationRequest.memberContext.array()
                )
            )
        }
    }
}
