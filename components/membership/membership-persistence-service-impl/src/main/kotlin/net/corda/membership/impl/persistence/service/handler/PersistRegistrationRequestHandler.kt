package net.corda.membership.impl.persistence.service.handler

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
            val now = clock.instant()
            val currentStatus = em.find(
                RegistrationRequestEntity::class.java,
                registrationId,
                LockModeType.PESSIMISTIC_WRITE,
            )
            if (currentStatus?.status?.toStatus()?.canMoveToStatus(request.status) == false) {
                logger.info(
                    "Registration request [$registrationId] has status: ${currentStatus.status}" +
                        " can not move it to status ${request.status}"
                )
                return@transaction
            }
            em.merge(
                with(request.registrationRequest) {
                    RegistrationRequestEntity(
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
            )
        }
    }
}
