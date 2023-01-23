package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryPreAuthToken
import net.corda.data.membership.db.response.query.PreAuthTokenQueryResponse
import net.corda.membership.datamodel.PreAuthTokenEntity
import net.corda.membership.impl.persistence.service.handler.RevokePreAuthTokenHandler.Companion.toAvro
import net.corda.virtualnode.toCorda
import javax.persistence.criteria.Predicate

internal class QueryPreAuthTokenHandler(persistenceHandlerServices: PersistenceHandlerServices) :
    BasePersistenceHandler<QueryPreAuthToken, PreAuthTokenQueryResponse>(persistenceHandlerServices) {

    override fun invoke(context: MembershipRequestContext, request: QueryPreAuthToken): PreAuthTokenQueryResponse {
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val criteriaBuilder = em.criteriaBuilder
            val criteriaQuery = criteriaBuilder.createQuery(PreAuthTokenEntity::class.java)
            val root = criteriaQuery.from(PreAuthTokenEntity::class.java)

            val predicates = mutableListOf<Predicate>()
            request.tokenId?.let { predicates.add(criteriaBuilder.equal(root.get<String>("tokenId"), it)) }
            request.statuses?.let { requestPreAuthTokenStatuses ->
                val inStatus = criteriaBuilder.`in`(root.get<String>("status"))
                requestPreAuthTokenStatuses.forEach { requestPreAuthTokenStatus ->
                    inStatus.value(requestPreAuthTokenStatus.toString())
                }
                predicates.add(inStatus)
            }
            request.ownerX500Name?.let { predicates.add(criteriaBuilder.equal(root.get<String>("x500Name"), it)) }

            @Suppress("SpreadOperator")
            val query = criteriaQuery.select(root).where(*predicates.toTypedArray())
            PreAuthTokenQueryResponse(em.createQuery(query).resultList.map { it.toAvro() })
        }
    }
}