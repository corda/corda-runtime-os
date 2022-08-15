package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.UpdateRegistrationRequestStatus
import net.corda.data.membership.rpc.response.RegistrationStatus
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.virtualnode.toCorda

internal class UpdateRegistrationRequestStatusHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<UpdateRegistrationRequestStatus, Unit>(persistenceHandlerServices) {
    override fun invoke(context: MembershipRequestContext, request: UpdateRegistrationRequestStatus) {
        transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val registrationRequest = em.find(
                RegistrationRequestEntity::class.java,
                request.registrationId
            ) ?: throw MembershipPersistenceException("Could not find registration request: ${request.registrationId}")
            val currentStatus = RegistrationStatus.valueOf(registrationRequest.status)
            if (currentStatus.ordinal > request.registrationStatus.ordinal) {
                throw MembershipPersistenceException("Could not update status from $currentStatus to ${request.registrationStatus}")
            }
            registrationRequest.status = request.registrationStatus.name
            registrationRequest.lastModified = clock.instant()
            em.merge(registrationRequest)
        }
    }
}
