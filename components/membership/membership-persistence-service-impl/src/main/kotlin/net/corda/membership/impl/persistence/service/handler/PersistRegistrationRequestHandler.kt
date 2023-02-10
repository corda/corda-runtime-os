package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.membership.datamodel.MemberSignatureEntity
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.impl.persistence.service.handler.RegistrationStatusHelper.canMoveToStatus
import net.corda.membership.impl.persistence.service.handler.RegistrationStatusHelper.toStatus
import net.corda.virtualnode.toCorda
import javax.persistence.LockModeType

internal class PersistRegistrationRequestHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistRegistrationRequest, Unit>(persistenceHandlerServices) {
    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroSerializer {
            logger.error("Failed to serialize key value pair list.")
        }

    override fun invoke(context: MembershipRequestContext, request: PersistRegistrationRequest) {
        val registrationId = request.registrationRequest.registrationId
        logger.info("Persisting registration request with ID [$registrationId].")
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
                RegistrationRequestEntity(
                    registrationId = request.registrationRequest.registrationId,
                    holdingIdentityShortHash = request.registeringHoldingIdentity.toCorda().shortHash.value,
                    status = request.status.toString(),
                    created = now,
                    lastModified = now,
                    context = request.registrationRequest.memberContext.array(),
                )
            )
            em.merge(
                MemberSignatureEntity(
                    groupId = request.registeringHoldingIdentity.groupId,
                    memberX500Name = request.registeringHoldingIdentity.x500Name,
                    publicKey = request.registrationRequest.memberSignature.publicKey.array(),
                    context = keyValuePairListSerializer.serialize(request.registrationRequest.memberSignature.context) ?: byteArrayOf(),
                    content = request.registrationRequest.memberSignature.bytes.array(),
                )
            )
        }
    }
}
