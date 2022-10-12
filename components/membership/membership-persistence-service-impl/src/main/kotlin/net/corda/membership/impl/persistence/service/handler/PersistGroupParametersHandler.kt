package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistGroupParameters
import net.corda.data.membership.db.response.command.PersistGroupParametersResponse
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.toMap
import net.corda.virtualnode.toCorda

internal class PersistGroupParametersHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistGroupParameters, PersistGroupParametersResponse>(persistenceHandlerServices) {
    private companion object {
        const val EPOCH_KEY = "corda.epoch"
    }
    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroSerializer {
            logger.error("Failed to serialize key value pair list.")
        }

    private fun serializeProperties(context: KeyValuePairList): ByteArray {
        return keyValuePairListSerializer.serialize(context) ?: throw MembershipPersistenceException(
            "Failed to serialize key value pair list."
        )
    }

    override fun invoke(
        context: MembershipRequestContext,
        request: PersistGroupParameters
    ): PersistGroupParametersResponse {
        val epoch = transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val epochFromRequest = request.groupParameters.toMap()[EPOCH_KEY].toString().toInt()
            em.createQuery(
                "SELECT c FROM ${GroupParametersEntity::class.simpleName} c " +
                        "ORDER BY c.epoch DESC",
                GroupParametersEntity::class.java
            ).resultList.firstOrNull()?.epoch?.let {
                require(epochFromRequest > it) {
                    throw MembershipPersistenceException("Group parameters with epoch=$epochFromRequest already exist.")
                }
            }
            
            val entity = GroupParametersEntity(
                epoch = epochFromRequest,
                parameters = serializeProperties(request.groupParameters),
            )
            em.persist(entity)

            entity.epoch
        }

        return PersistGroupParametersResponse(epoch)
    }
}
