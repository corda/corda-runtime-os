package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistGroupPolicy
import net.corda.data.membership.db.response.command.PersistGroupPolicyResponse
import net.corda.membership.datamodel.GroupPolicyEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.virtualnode.toCorda

internal class PersistGroupPolicyHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistGroupPolicy, PersistGroupPolicyResponse>(persistenceHandlerServices) {
    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroSerializer {
            logger.error("Failed to serialize key value pair list.")
        }
    private fun serializeProperties(context: KeyValuePairList): ByteArray {
        return keyValuePairListSerializer.serialize(context) ?: throw MembershipPersistenceException(
            "Failed to serialize key value pair list."
        )
    }

    override fun invoke(context: MembershipRequestContext, request: PersistGroupPolicy): PersistGroupPolicyResponse {
        val version = transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val entity = GroupPolicyEntity(
                version = null,
                createdAt = clock.instant(),
                properties = serializeProperties(request.properties),
            )
            em.persist(entity)

            entity.version
        }

        return PersistGroupPolicyResponse(version)
    }
}
