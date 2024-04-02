package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.impl.persistence.service.RecoverableException
import net.corda.membership.impl.persistence.service.handler.RegistrationStatusHelper.toStatus
import net.corda.membership.lib.registration.RegistrationStatusExt.canMoveToStatus
import net.corda.virtualnode.toCorda
import java.time.Instant
import javax.persistence.LockModeType

internal class PersistRegistrationRequestHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistRegistrationRequest, Unit>(persistenceHandlerServices) {
    override val operation = PersistRegistrationRequest::class.java
    override fun invoke(context: MembershipRequestContext, request: PersistRegistrationRequest) {
        val registrationId = request.registrationRequest.registrationId
        logger.info("Persisting registration request with ID [$registrationId] to status ${request.status}.")
        transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val currentRegistrationRequest = em.find(
                RegistrationRequestEntity::class.java,
                registrationId,
                LockModeType.PESSIMISTIC_WRITE,
            )
            if ((currentRegistrationRequest == null) && (!request.create)) {
                // This request should not create a new one, but we couldn't find one, so let's try again later
                throw RecoverableException("Could not find registration request: $registrationId")
            }
            currentRegistrationRequest?.status?.toStatus()?.let {
                if (it == request.status) {
                    logger.info(
                        "Registration request [$registrationId] with status: ${currentRegistrationRequest.status}" +
                            " is already persisted. Persistence request was discarded."
                    )
                    return@transaction
                }
                if (!it.canMoveToStatus(request.status)) {
                    logger.info(
                        "Registration request [$registrationId] has status: ${currentRegistrationRequest.status}" +
                            " can not move it to status ${request.status}"
                    )
                    // In case of processing persistence requests in an unordered manner we need to make sure the serial
                    // gets persisted. All other existing data of the request will remain the same.
                    if (request.status == RegistrationStatus.SENT_TO_MGM && currentRegistrationRequest.serial == null) {
                        logger.info("Updating request [$registrationId] serial to ${request.registrationRequest.serial}")
                        currentRegistrationRequest.serial = request.registrationRequest.serial
                        currentRegistrationRequest.lastModified = clock.instant()
                        em.merge(
                            currentRegistrationRequest
                        )
                        return@transaction
                    }
                    return@transaction
                }
            }
            em.merge(createEntityBasedOnRequest(request, currentRegistrationRequest?.created))
        }
    }

    private fun createEntityBasedOnRequest(
        request: PersistRegistrationRequest,
        created: Instant?,
    ): RegistrationRequestEntity {
        val now = clock.instant()
        with(request.registrationRequest) {
            return RegistrationRequestEntity(
                registrationId = registrationId,
                holdingIdentityShortHash = request.registeringHoldingIdentity.toCorda().shortHash.value,
                status = request.status.toString(),
                created = created ?: now,
                lastModified = now,
                memberContext = memberContext.data.array(),
                memberContextSignatureKey = memberContext.signature.publicKey.array(),
                memberContextSignatureContent = memberContext.signature.bytes.array(),
                memberContextSignatureSpec = memberContext.signatureSpec.signatureName,
                registrationContext = registrationContext.data.array(),
                registrationContextSignatureKey = registrationContext.signature.publicKey.array(),
                registrationContextSignatureContent = registrationContext.signature.bytes.array(),
                registrationContextSignatureSpec = registrationContext.signatureSpec.signatureName,
                serial = serial,
            )
        }
    }
}
