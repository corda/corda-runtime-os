package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryPreAuthToken
import net.corda.data.membership.db.response.query.PreAuthTokenQueryResponse
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.data.membership.preauth.PreAuthTokenStatus
import net.corda.membership.datamodel.PreAuthTokenEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.virtualnode.toCorda

internal class QueryPreAuthTokenHandler(persistenceHandlerServices: PersistenceHandlerServices) :
    BasePersistenceHandler<QueryPreAuthToken, PreAuthTokenQueryResponse>(persistenceHandlerServices) {

    override fun invoke(context: MembershipRequestContext, request: QueryPreAuthToken): PreAuthTokenQueryResponse {
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            if (request.tokenId != null) {
                val token = em.find(PreAuthTokenEntity::class.java, request.tokenId)?.toAvro() ?:
                    throw MembershipPersistenceException("Could not find pre auth token with id ${request.tokenId}.")
                if (!request.statuses.contains(token.status)) {
                    PreAuthTokenQueryResponse(emptyList())
                } else if (request.ownerX500Name != token.ownerX500Name) {
                    PreAuthTokenQueryResponse(emptyList())
                } else {
                    PreAuthTokenQueryResponse(listOf(token))
                }
            } else if (request.ownerX500Name != null && request.statuses != null) {
                val result = em.createQuery(
                    "SELECT t FROM ${PreAuthTokenEntity::class.java.simpleName} t WHERE t.ownerX500Name = :x500Name" +
                        " AND t.status IN (:statuses)",
                    PreAuthTokenEntity::class.java
                )
                    .setParameter("x500Name", request.ownerX500Name)
                    .setParameter("statuses", request.statuses.map { it.toString() })
                PreAuthTokenQueryResponse(result.resultList.map { it.toAvro() })
            } else if (request.ownerX500Name != null) {
                val result = em.createQuery(
                    "SELECT t FROM ${PreAuthTokenEntity::class.java.simpleName} t WHERE t.ownerX500Name = :x500Name",
                    PreAuthTokenEntity::class.java
                )
                    .setParameter("x500Name", request.ownerX500Name)
                PreAuthTokenQueryResponse(result.resultList.map { it.toAvro() })
            } else if (request.statuses != null) {
                val result = em.createQuery(
                    "SELECT t FROM ${PreAuthTokenEntity::class.java.simpleName} t WHERE t.status IN (:statuses)",
                    PreAuthTokenEntity::class.java
                )
                    .setParameter("statuses", request.statuses.map { it.toString() })
                PreAuthTokenQueryResponse(result.resultList.map { it.toAvro() })
            } else {
                PreAuthTokenQueryResponse(emptyList())
            }
        }
    }

    private fun PreAuthTokenEntity.toAvro(): PreAuthToken {
        return PreAuthToken(this.tokenId, this.ownerX500Name, this.ttl.toEpochMilli(), PreAuthTokenStatus.valueOf(this.status), this.remark)
    }

}