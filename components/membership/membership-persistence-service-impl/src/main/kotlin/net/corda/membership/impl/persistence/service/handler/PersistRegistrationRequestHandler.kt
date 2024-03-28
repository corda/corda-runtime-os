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
    override fun invoke(context: MembershipRequestContext, request: PersistRegistrationRequest) {
        val registrationId = request.registrationRequest.registrationId
        logger.info("Persisting registration request with ID [$registrationId] to status ${request.status}.")
        logger.info(
            "QQQ PersistRegistrationRequestHandler with" +
                " $registrationId and ${request.status}, context: ${context.requestId} 1"
        )
        try {
            transaction(context.holdingIdentity.toCorda().shortHash) { em ->
                val currentRegistrationRequest = em.find(
                    RegistrationRequestEntity::class.java,
                    registrationId,
                    LockModeType.PESSIMISTIC_WRITE,
                )
                logger.info(
                    "QQQ PersistRegistrationRequestHandler with" +
                        " $registrationId and ${request.status}, context: ${context.requestId} 2"
                )
                if (currentRegistrationRequest == null) {
                    logger.info(
                        "QQQ PPP22 I don't know about this one with" +
                                " $registrationId and ${request.status}, context: ${context.requestId} 2"
                    )
                }
                currentRegistrationRequest?.status?.toStatus()?.let {
                    logger.info(
                        "QQQ PersistRegistrationRequestHandler with" +
                            " $registrationId and ${request.status}, context: ${context.requestId} 3"
                    )
                    if (it == request.status) {
                        logger.info(
                            "Registration request [$registrationId] with status: ${currentRegistrationRequest.status}" +
                                " is already persisted. Persistence request was discarded."
                        )
                        logger.info(
                            "QQQ PersistRegistrationRequestHandler with" +
                                " $registrationId and ${request.status}, context: ${context.requestId} 4"
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
                        logger.info(
                            "QQQ PersistRegistrationRequestHandler with" +
                                " $registrationId and ${request.status}, context: ${context.requestId} 5"
                        )
                        if (request.status == RegistrationStatus.SENT_TO_MGM && currentRegistrationRequest.serial == null) {
                            logger.info("Updating request [$registrationId] serial to ${currentRegistrationRequest.serial}")
                            em.merge(
                                createEntityBasedOnPreviousEntity(
                                    currentRegistrationRequest,
                                    request.registrationRequest.serial
                                )
                            )
                            logger.info(
                                "QQQ PersistRegistrationRequestHandler with" +
                                    " $registrationId and ${request.status}, context: ${context.requestId} 6"
                            )
                            return@transaction
                        }
                        logger.info(
                            "QQQ PersistRegistrationRequestHandler with" +
                                " $registrationId and ${request.status}, context: ${context.requestId} 7"
                        )
                        return@transaction
                    }
                    logger.info(
                        "QQQ PersistRegistrationRequestHandler with" +
                            " $registrationId and ${request.status}, context: ${context.requestId} 8"
                    )
                }
                logger.info(
                    "QQQ PersistRegistrationRequestHandler with" +
                        " $registrationId and ${request.status}, context: ${context.requestId} 9"
                )
                em.merge(createEntityBasedOnRequest(request))
                logger.info(
                    "QQQ PersistRegistrationRequestHandler with" +
                        " $registrationId and ${request.status}, context: ${context.requestId} 10"
                )
            }
        } catch (e: Exception) {
            logger.info(
                "QQQ PersistRegistrationRequestHandler with" +
                    " $registrationId and ${request.status}, context: ${context.requestId} 11",
                e
            )
            throw e
        }
    }

    private fun createEntityBasedOnPreviousEntity(previousEntity: RegistrationRequestEntity, newSerial: Long): RegistrationRequestEntity {
        val now = clock.instant()
        with(previousEntity) {
            return RegistrationRequestEntity(
                registrationId = registrationId,
                holdingIdentityShortHash = holdingIdentityShortHash,
                status = status,
                created = created,
                lastModified = now,
                memberContext = memberContext,
                memberContextSignatureKey = memberContextSignatureKey,
                memberContextSignatureContent = memberContextSignatureContent,
                memberContextSignatureSpec = memberContextSignatureSpec,
                registrationContext = registrationContext,
                registrationContextSignatureKey = registrationContextSignatureKey,
                registrationContextSignatureContent = registrationContextSignatureContent,
                registrationContextSignatureSpec = registrationContextSignatureSpec,
                serial = newSerial,
            )
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
