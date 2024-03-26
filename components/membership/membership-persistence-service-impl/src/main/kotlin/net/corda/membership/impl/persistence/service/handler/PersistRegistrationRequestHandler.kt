package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.impl.persistence.service.handler.RegistrationStatusHelper.toStatus
import net.corda.membership.lib.registration.RegistrationStatusExt.canMoveToStatus
import net.corda.virtualnode.toCorda
import javax.persistence.LockModeType

internal class PersistRegistrationRequestHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistRegistrationRequest, Unit>(persistenceHandlerServices) {
    override val operation = PersistRegistrationRequest::class.java
    //private val id = Exception(UUID.randomUUID().toString())
    override fun invoke(context: MembershipRequestContext, request: PersistRegistrationRequest) {
        val registrationId = request.registrationRequest.registrationId
        //val run = Exception("${Thread.currentThread().id} - ${UUID.randomUUID()} for $registrationId", id)
        logger.info("Persisting registration request with ID [$registrationId] to status ${request.status}.")
        logger.info("QQQ Persisting registration request with ID [$registrationId] to status ${request.status}.")
        try {
            transaction(context.holdingIdentity.toCorda().shortHash) { em ->
                val currentRegistrationRequest = em.find(
                    RegistrationRequestEntity::class.java,
                    registrationId,
                    LockModeType.PESSIMISTIC_WRITE,
                )
                val status = currentRegistrationRequest?.status?.toStatus()
                if (status == null) {
                    //val e = Exception("Gooing to merge ${Thread.currentThread().id}", run)
                    logger.info("QQQ for [$registrationId] going 2.")
                    em.persist(createEntityBasedOnRequest(request))
                } else {
                    if (status == request.status) {
                        logger.info(
                            "Registration request [$registrationId] with status: ${currentRegistrationRequest.status}" +
                                    " is already persisted. Persistence request was discarded."
                        )
                    }
                    else if (!status.canMoveToStatus(request.status)) {
                        logger.info(
                            "Registration request [$registrationId] has status: ${currentRegistrationRequest.status}" +
                                    " can not move it to status ${request.status}"
                        )
                        // In case of processing persistence requests in an unordered manner we need to make sure the serial
                        // gets persisted. All other existing data of the request will remain the same.
                        if (request.status == RegistrationStatus.SENT_TO_MGM && currentRegistrationRequest.serial == null) {
                            logger.info("Updating request [$registrationId] serial to ${request.registrationRequest.serial}")
                            logger.info("QQQ for [$registrationId] going 1.")
                            //em.merge(createEntityBasedOnPreviousEntity(currentRegistrationRequest, request.registrationRequest.serial))
                            val now = clock.instant()
                            currentRegistrationRequest.serial = request.registrationRequest.serial
                            currentRegistrationRequest.lastModified = now
                        }
                    }
                }

            }
        } catch (e: Exception) {
            logger.info("QQQ Got exception for registrationId: $e", e)
            throw e
        }
    }

    private fun createEntityBasedOnRequest(request: PersistRegistrationRequest): RegistrationRequestEntity {
        val now = clock.instant()
        with(request.registrationRequest) {
            return RegistrationRequestEntity(
                registrationId = registrationId,
                holdingIdentityShortHash = request.registeringHoldingIdentity.toCorda().shortHash.value,
                status = request.status.toString(),
                created = now,
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
