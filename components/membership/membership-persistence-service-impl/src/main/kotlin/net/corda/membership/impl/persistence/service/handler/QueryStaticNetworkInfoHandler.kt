package net.corda.membership.impl.persistence.service.handler

import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryStaticNetworkInfo
import net.corda.data.membership.db.response.query.StaticNetworkInfoQueryResponse
import net.corda.membership.datamodel.StaticNetworkInfoEntity
import net.corda.membership.network.writer.staticnetwork.StaticNetworkInfoMappingUtils.toAvro
import net.corda.utilities.trace

internal class QueryStaticNetworkInfoHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<QueryStaticNetworkInfo, StaticNetworkInfoQueryResponse>(persistenceHandlerServices) {
    override val operation = QueryStaticNetworkInfo::class.java
    private val deserializer = cordaAvroSerializationFactory.createAvroDeserializer({
        logger.error("Failed to deserialize KeyValuePairList.")
    }, KeyValuePairList::class.java)

    override fun invoke(
        context: MembershipRequestContext,
        request: QueryStaticNetworkInfo
    ): StaticNetworkInfoQueryResponse {
        val groupId = request.groupId

        logger.trace { "Retrieving current static network information for network $groupId." }

        return transaction { em ->
            StaticNetworkInfoQueryResponse(
                em.find(StaticNetworkInfoEntity::class.java, groupId)?.toAvro(deserializer)
            )
        }
    }
}
