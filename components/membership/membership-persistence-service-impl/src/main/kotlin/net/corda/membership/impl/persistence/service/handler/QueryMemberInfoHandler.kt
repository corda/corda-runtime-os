package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryMemberInfo
import net.corda.data.membership.db.response.query.MemberInfoQueryResponse
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.virtualnode.toCorda

internal class QueryMemberInfoHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<QueryMemberInfo, MemberInfoQueryResponse>(persistenceHandlerServices) {

    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java
        )
    }

    override fun invoke(context: MembershipRequestContext, request: QueryMemberInfo): MemberInfoQueryResponse {
        logger.info("Querying for ${request.queryIdentities.size} identities")
        return if (request.queryIdentities.isEmpty()) {
            logger.info("Query list is empty. Skipping DB query and returning empty list.")
            MemberInfoQueryResponse(emptyList())
        } else {
            logger.info("Querying for ${request.queryIdentities.size} MemberInfo(s).")
            MemberInfoQueryResponse(
                transaction(context.holdingIdentity.toCorda().id) { em ->
                    request.queryIdentities.mapNotNull { holdingIdentity ->
                        em.find(
                            MemberInfoEntity::class.java,
                            MemberInfoEntityPrimaryKey(
                                holdingIdentity.groupId,
                                holdingIdentity.x500Name
                            )
                        )
                    }.map {
                        PersistentMemberInfo(
                            context.holdingIdentity,
                            keyValuePairListDeserializer.deserialize(it.memberContext),
                            keyValuePairListDeserializer.deserialize(it.mgmContext)
                        )
                    }
                }
            )
        }
    }
}