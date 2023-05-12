package net.corda.membership.db.lib

import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.db.lib.RegistrationStatusHelper.toStatus
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.lib.registration.RegistrationStatusExt.canMoveToStatus
import net.corda.utilities.time.Clock
import org.slf4j.LoggerFactory
import javax.persistence.EntityManager
import javax.persistence.LockModeType

class PersistRegistrationRequestService(
    private val clock: Clock,
) {
    private companion object {
        val logger = LoggerFactory.getLogger(PersistRegistrationRequestService::class.java)
    }
    fun persist(
        em: EntityManager,
        request: RegistrationRequest,
    ) {
        val now = clock.instant()
        val currentStatus = em.find(
            RegistrationRequestEntity::class.java,
            request.registrationId,
            LockModeType.PESSIMISTIC_WRITE,
        )
        if (currentStatus?.status?.toStatus()?.canMoveToStatus(request.status) == false) {
            logger.info(
                "Registration request [${request.registrationId}] has status: ${currentStatus.status}" +
                    " can not move it to status ${request.status}",
            )
            return
        }
        em.merge(
            with(request) {
                RegistrationRequestEntity(
                    registrationId = registrationId,
                    holdingIdentityShortHash = request.requester.shortHash.value,
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
            },
        )
    }
}
