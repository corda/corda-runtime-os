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
        // val id = UUID.randomUUID()
        // logger.info("QQQ 1 for $registrationId with $id requestId: ${context.requestId}")
        logger.info("Persisting registration request with ID [$registrationId] to status ${request.status}.")
        transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            // logger.info("QQQ 2 for $registrationId with $id")
            val currentRegistrationRequest = em.find(
                RegistrationRequestEntity::class.java,
                registrationId,
                LockModeType.PESSIMISTIC_WRITE,
            )
            val toMerge = getOrCreateEntity(currentRegistrationRequest, request)
            if (toMerge != null) {
                em.merge(toMerge)
            }
        }
    }

    private fun getOrCreateEntity(
        currentEntity: RegistrationRequestEntity?,
        request: PersistRegistrationRequest,
    ): RegistrationRequestEntity? {
        return if (currentEntity != null) {
            getEntityToMerge(currentEntity, request)
        } else {
            // logger.info("QQQ 10 for $registrationId with $id")
            createEntityBasedOnRequest(request)
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

    private fun getEntityToMerge(
        currentEntity: RegistrationRequestEntity,
        request: PersistRegistrationRequest,
    ): RegistrationRequestEntity? {
        val now = clock.instant()
        val registrationId = request.registrationRequest.registrationId
        val status = currentEntity.status.toStatus()
        return if (request.status == status) {
            // logger.info("QQQ 5 for $registrationId")
            logger.info(
                "Registration request [$registrationId] with status: $status" +
                    " is already persisted. Persistence request was discarded."
            )
            null
        } else if (!status.canMoveToStatus(request.status)) {
            // In case of processing persistence requests in an unordered manner we need to make sure the serial
            // gets persisted. All other existing data of the request will remain the same.
            if (request.status == RegistrationStatus.SENT_TO_MGM && currentEntity.serial == null) {
                // logger.info("QQQ 7 for $registrationId with $id")
                logger.info("Updating request [$registrationId] serial to ${request.registrationRequest.serial}")
                currentEntity.serial = request.registrationRequest.serial
                currentEntity.lastModified = now
                currentEntity
            } else {
                // logger.info("QQQ 6 for $registrationId with $id")
                logger.info(
                    "Registration request [$registrationId] has status: ${currentEntity.status}" +
                        " can not move it to status ${request.status}"
                )
                null
            }
        } else {
            currentEntity.status = request.status.toString()
            currentEntity.serial = request.registrationRequest.serial
            currentEntity.lastModified = now
            // logger.info("QQQ 9 for $registrationId with $id")
            currentEntity
        }
    }
}
