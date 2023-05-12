package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryMemberInfo
import net.corda.data.membership.db.response.query.MemberInfoQueryResponse
import net.corda.membership.db.lib.QueryMemberInfoService
import net.corda.virtualnode.toCorda

internal class QueryMemberInfoHandler(
    persistenceHandlerServices: PersistenceHandlerServices,
) : BasePersistenceHandler<QueryMemberInfo, MemberInfoQueryResponse>(persistenceHandlerServices) {

    private val getter = QueryMemberInfoService(cordaAvroSerializationFactory)

    override fun invoke(context: MembershipRequestContext, request: QueryMemberInfo): MemberInfoQueryResponse {
        logger.info("Querying for ${request.queryIdentities.size} identities")
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val infos = getter.get(
                em,
                context.holdingIdentity,
                request.queryIdentities,
            )
            MemberInfoQueryResponse(
                infos.toList(),
            )
        }
    }
}
