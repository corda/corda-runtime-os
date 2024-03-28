package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.UpdateRegistrationRequestStatus
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.impl.persistence.service.RecoverableException
import net.corda.membership.impl.persistence.service.handler.RegistrationStatusHelper.toStatus
import net.corda.membership.lib.registration.RegistrationStatusExt.canMoveToStatus
import net.corda.virtualnode.toCorda
import javax.persistence.LockModeType

internal class UpdateRegistrationRequestStatusHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<UpdateRegistrationRequestStatus, Unit>(persistenceHandlerServices) {
    override val operation = UpdateRegistrationRequestStatus::class.java
    override fun invoke(context: MembershipRequestContext, request: UpdateRegistrationRequestStatus) {
        transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val registrationRequest = em.find(
                RegistrationRequestEntity::class.java,
                request.registrationId,
                LockModeType.PESSIMISTIC_WRITE,
            ) ?: // Throwing RecoverableException to allow the request to be retried if the registration
                // request was not found. As the request can be persistent asynchronously, we can get the
                // update state request before the first persistence request.
                throw RecoverableException("Could not find registration request: ${request.registrationId}")
            val currentStatus = registrationRequest.status.toStatus()
            if (!currentStatus.canMoveToStatus(request.registrationStatus)) {
                if ((request.serial != null) && (registrationRequest.serial == null)) {
                    registrationRequest.serial = request.serial
                    em.merge(registrationRequest)
                } else {
                    logger.info(
                        "Could not update status of registration ${request.registrationId} from $currentStatus to " +
                            "${request.registrationStatus}, will ignore the update"
                    )
                }
            } else {
                logger.info(
                    "Updating registration request ${request.registrationId} status from $currentStatus" +
                        " to ${request.registrationStatus}",
                )
                registrationRequest.status = request.registrationStatus.name
                registrationRequest.lastModified = clock.instant()
                registrationRequest.reason = request.reason
                if (request.serial != null) {
                    registrationRequest.serial = request.serial
                }
                em.merge(registrationRequest)
            }
        }
    }
}
