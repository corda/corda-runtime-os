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
        transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val currentStatus = em.find(
                RegistrationRequestEntity::class.java,
                registrationId,
                LockModeType.PESSIMISTIC_WRITE,
            )
            currentStatus?.status?.toStatus()?.let {
                if (it == request.status) {
                    logger.info("Registration request [$registrationId] with status: ${currentStatus.status}" +
                            " is already persisted. Persistence request was discarded.")
                    return@transaction
                }
                // In case of processing persistence requests in an unordered manner we need to make sure the serial
                // gets persisted. The existing status of the request won't be modified.
                if (request.status == RegistrationStatus.SENT_TO_MGM) {
                    em.merge(createEntity(request, currentStatus.status.toStatus()))
                    return@transaction
                } else if (!it.canMoveToStatus(request.status)) {
                    logger.info(
                        "Registration request [$registrationId] has status: ${currentStatus.status}" +
                                " can not move it to status ${request.status}"
                    )
                    return@transaction
                }
            }
            em.merge(createEntity(request, request.status))
        }
    }

    private fun createEntity(request: PersistRegistrationRequest, status: RegistrationStatus): RegistrationRequestEntity {
        val now = clock.instant()
        with(request.registrationRequest) {
            return RegistrationRequestEntity(
                registrationId = registrationId,
                holdingIdentityShortHash = request.registeringHoldingIdentity.toCorda().shortHash.value,
                status = status.toString(),
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
