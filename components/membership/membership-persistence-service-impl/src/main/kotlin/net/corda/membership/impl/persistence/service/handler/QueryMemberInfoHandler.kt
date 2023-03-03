package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryMemberInfo
import net.corda.data.membership.db.response.query.MemberInfoQueryResponse
import net.corda.membership.datamodel.MemberInfoEntity
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
            logger.info("Query filter list is empty. Returning full member list.")
            MemberInfoQueryResponse(
                transaction(context.holdingIdentity.toCorda().shortHash) { em ->
                    em.createQuery(
                        "SELECT m FROM ${MemberInfoEntity::class.simpleName} m",
                        MemberInfoEntity::class.java
                    ).resultList
                }.map {
                    it.toPersistentMemberInfo(context.holdingIdentity)
                }
            )
        } else {
            logger.info("Querying for ${request.queryIdentities.size} members MemberInfo(s).")
            MemberInfoQueryResponse(
                transaction(context.holdingIdentity.toCorda().shortHash) { em ->
                    request.queryIdentities.flatMap { holdingIdentity ->
                        em.createQuery(
                            "SELECT m FROM ${MemberInfoEntity::class.simpleName} " +
                                    "m where m.groupId = :groupId and m.memberX500Name = :memberX500Name",
                            MemberInfoEntity::class.java
                        )
                            .setParameter("groupId", holdingIdentity.groupId)
                            .setParameter("memberX500Name", holdingIdentity.x500Name)
                            .resultList
                    }.map {
                        it.toPersistentMemberInfo(context.holdingIdentity)
                    }
                }
            )
        }
    }

    private fun MemberInfoEntity.toPersistentMemberInfo(viewOwningMember: net.corda.data.identity.HoldingIdentity) =
        PersistentMemberInfo(
            viewOwningMember,
            keyValuePairListDeserializer.deserialize(this.memberContext),
            keyValuePairListDeserializer.deserialize(this.mgmContext)
        )
}
