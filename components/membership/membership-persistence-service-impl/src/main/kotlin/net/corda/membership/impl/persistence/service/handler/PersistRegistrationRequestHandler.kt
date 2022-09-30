package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.membership.datamodel.MemberSignatureEntity
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.lib.toMap
import net.corda.virtualnode.toCorda

internal class PersistRegistrationRequestHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistRegistrationRequest, Unit>(persistenceHandlerServices) {
    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroSerializer {
            logger.error("Failed to serialize key value pair list.")
        }

    override fun invoke(context: MembershipRequestContext, request: PersistRegistrationRequest) {
        logger.info("Persisting registration request with ID [${request.registrationRequest.registrationId}].")
        println("QQQ persisting member signature for " +
                "${request.registeringHoldingIdentity.x500Name} " +
                "context is ${request.registrationRequest.memberSignature.context.toMap()}")
        transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val now = clock.instant()
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
