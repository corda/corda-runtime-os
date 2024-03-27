package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.impl.persistence.service.handler.RegistrationStatusHelper.toStatus
import net.corda.membership.lib.registration.RegistrationStatusExt.canMoveToStatus
import net.corda.virtualnode.toCorda
import java.util.UUID
import javax.persistence.LockModeType

internal class PersistRegistrationRequestHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistRegistrationRequest, Unit>(persistenceHandlerServices) {
    override val operation = PersistRegistrationRequest::class.java
    override fun invoke(context: MembershipRequestContext, request: PersistRegistrationRequest) {
        val registrationId = request.registrationRequest.registrationId
        val id = UUID.randomUUID()
        logger.info("QQQ 1 for $registrationId with $id")
        logger.info("Persisting registration request with ID [$registrationId] to status ${request.status}.")
        try {
            transaction(context.holdingIdentity.toCorda().shortHash) { em ->
                logger.info("QQQ 2 for $registrationId with $id")
                val currentRegistrationRequest = em.find(
                    RegistrationRequestEntity::class.java,
                    registrationId,
                    LockModeType.PESSIMISTIC_WRITE,
                )
                val now = clock.instant()
                logger.info("QQQ 3 for $registrationId with $id")
                if (currentRegistrationRequest != null) {
                    val status = currentRegistrationRequest.status.toStatus()
                    logger.info("QQQ 4 for $registrationId with $id $status")
                    if (request.status == status) {
                        logger.info("QQQ 5 for $registrationId")
                        logger.info(
                            "Registration request [$registrationId] with status: ${currentRegistrationRequest.status}" +
                                " is already persisted. Persistence request was discarded."
                        )
                    } else if (!status.canMoveToStatus(request.status)) {
                        logger.info("QQQ 6 for $registrationId with $id")
                        logger.info(
                            "Registration request [$registrationId] has status: ${currentRegistrationRequest.status}" +
                                " can not move it to status ${request.status}"
                        )
                        // In case of processing persistence requests in an unordered manner we need to make sure the serial
                        // gets persisted. All other existing data of the request will remain the same.
                        if (request.status == RegistrationStatus.SENT_TO_MGM && currentRegistrationRequest.serial == null) {
                            logger.info("QQQ 7 for $registrationId with $id")
                            logger.info("Updating request [$registrationId] serial to ${request.registrationRequest.serial}")
                            currentRegistrationRequest.serial = request.registrationRequest.serial
                            currentRegistrationRequest.lastModified = now
                            em.merge(currentRegistrationRequest)
                        }
                    } else {
                        logger.info("QQQ 8 for $registrationId with $id")
                        if (currentRegistrationRequest.memberContext !=
                            request.registrationRequest.memberContext.data.array()) {
                            logger.info("QQQ 8.1 for $registrationId with $id memberContext had changed")
                        }
                        if (currentRegistrationRequest.memberContextSignatureKey !=
                            request.registrationRequest.memberContext.signature.publicKey.array()) {
                            logger.info("QQQ 8.2 for $registrationId with $id memberContextSignatureKey had changed")
                        }
                        if (currentRegistrationRequest.memberContextSignatureKey !=
                            request.registrationRequest.memberContext.signature.publicKey.array()) {
                            logger.info("QQQ 8.3 for $registrationId with $id memberContextSignatureKey had changed")
                        }
                        currentRegistrationRequest.status = request.status.toString()
                        currentRegistrationRequest.serial = request.registrationRequest.serial
                        currentRegistrationRequest.lastModified = now
                        logger.info("QQQ 9 for $registrationId with $id")
                        em.merge(currentRegistrationRequest)
                    }
                } else {
                    logger.info("QQQ 10 for $registrationId with $id")
                    em.persist(createEntityBasedOnRequest(request))
                }
                logger.info("QQQ 11 for $registrationId with $id")
                logger.info("QQQ persisted ${request.registrationRequest.registrationId} in thread ${Thread.currentThread().id}")
            }
        } catch (e: Throwable) {
            logger.info("QQQ for $registrationId with $id got error: $e", e)
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
