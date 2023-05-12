package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryPreAuthToken
import net.corda.data.membership.db.response.query.PreAuthTokenQueryResponse
import net.corda.membership.db.lib.QueryPreAuthTokenService
import net.corda.virtualnode.toCorda

internal class QueryPreAuthTokenHandler(persistenceHandlerServices: PersistenceHandlerServices) :
    BasePersistenceHandler<QueryPreAuthToken, PreAuthTokenQueryResponse>(persistenceHandlerServices) {

    private val getter = QueryPreAuthTokenService()

    override fun invoke(context: MembershipRequestContext, request: QueryPreAuthToken): PreAuthTokenQueryResponse {
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val tokens = getter.query(
                em,
                request.tokenId,
                request.ownerX500Name,
                request.statuses,
            )
            PreAuthTokenQueryResponse(tokens.toList())
        }
    }
}
