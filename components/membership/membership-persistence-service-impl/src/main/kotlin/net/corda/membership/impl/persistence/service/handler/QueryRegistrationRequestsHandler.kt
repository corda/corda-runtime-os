package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryRegistrationRequests
import net.corda.data.membership.db.response.query.RegistrationRequestsQueryResponse
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.virtualnode.toCorda
import java.time.Instant

internal class QueryRegistrationRequestsHandler(persistenceHandlerServices: PersistenceHandlerServices)
    :BaseRequestStatusHandler<QueryRegistrationRequests, RegistrationRequestsQueryResponse>(persistenceHandlerServices)
{
    override fun invoke(
        context: MembershipRequestContext,
        request: QueryRegistrationRequests,
    ): RegistrationRequestsQueryResponse {
        val shortHash = context.holdingIdentity.toCorda().shortHash
        return transaction(shortHash) { em ->

            val criteriaBuilder = em.criteriaBuilder
            val queryBuilder = criteriaBuilder.createQuery(RegistrationRequestEntity::class.java)
            val root = queryBuilder.from(RegistrationRequestEntity::class.java)
            val query = queryBuilder
                .select(root)
                .where(
                    criteriaBuilder.and(
                        criteriaBuilder.equal(root.get<String>("holding_identity_id"), shortHash.value),
                    )
                ).orderBy(criteriaBuilder.asc(root.get<Instant>("created")))
            val details =
                em.createQuery(query)
                    .resultList
                    .map {
                        it.toDetails()
                    }
            RegistrationRequestsQueryResponse(details)
        }
    }

}
