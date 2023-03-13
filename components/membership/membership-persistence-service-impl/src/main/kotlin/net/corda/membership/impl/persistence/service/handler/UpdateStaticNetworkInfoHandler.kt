package net.corda.membership.impl.persistence.service.handler

import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.UpdateStaticNetworkInfo
import net.corda.data.membership.db.response.query.StaticNetworkInfoQueryResponse
import net.corda.membership.datamodel.StaticNetworkInfoEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.staticnetwork.StaticNetworkInfoMappingUtils.toAvro
import javax.persistence.LockModeType

internal class UpdateStaticNetworkInfoHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<UpdateStaticNetworkInfo, StaticNetworkInfoQueryResponse>(persistenceHandlerServices) {

    private val deserializer = cordaAvroSerializationFactory.createAvroDeserializer({
        logger.error("Failed to deserialize KeyValuePairList.")
    }, KeyValuePairList::class.java)

    private val serializer = cordaAvroSerializationFactory.createAvroSerializer<KeyValuePairList> {
        logger.error("Failed to serialize KeyValuePairList.")
    }

    override fun invoke(
        context: MembershipRequestContext,
        request: UpdateStaticNetworkInfo
    ): StaticNetworkInfoQueryResponse {
        val groupId = request.info.groupId
        logger.info("Attempting to update the static network information for network $groupId")
        return transaction { em ->
            val entity = em.find(
                StaticNetworkInfoEntity::class.java,
                groupId,
                LockModeType.PESSIMISTIC_WRITE
            ) ?: throw MembershipPersistenceException(
                "No existing static network configuration. Cannot update."
            )

            if (entity.version != request.info.version) {
                throw MembershipPersistenceException(
                    "Current persisted version of the static network information does not match the version in " +
                            "the request to update."
                )
            } else {
                // Only update the persisted record if the request contains a valid change.
                var updated = false

                // Update persisted group params.
                val currentParams = deserializer.deserialize(entity.groupParameters)
                val newParams = request.info.groupParameters
                if (currentParams != newParams) {
                    entity.groupParameters = serializer.serialize(newParams)
                        ?: throw MembershipPersistenceException("Could not serialize new group parameters.")
                    updated = true
                }

                if (updated) {
                    em.merge(entity)
                }
            }

            StaticNetworkInfoQueryResponse(entity.toAvro(deserializer))
        }
    }
}
