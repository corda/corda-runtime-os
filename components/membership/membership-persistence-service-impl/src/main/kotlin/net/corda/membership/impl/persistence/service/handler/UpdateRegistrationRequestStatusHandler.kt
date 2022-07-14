package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.UpdateRegistrationRequestStatus
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.virtualnode.toCorda

internal class UpdateRegistrationRequestStatusHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<UpdateRegistrationRequestStatus, Unit>(persistenceHandlerServices) {
    override fun invoke(context: MembershipRequestContext, request: UpdateRegistrationRequestStatus) {
        transaction(context.holdingIdentity.toCorda().id) { em ->
            val registrationRequest = em.find(
                RegistrationRequestEntity::class.java,
                request.registrationId
            ) ?: throw MembershipPersistenceException("Could not find registration request: ${request.registrationId}")
            registrationRequest.status = request.registrationStatus.name
            registrationRequest.lastModified = clock.instant()
            em.merge(registrationRequest)
        }
    }
}