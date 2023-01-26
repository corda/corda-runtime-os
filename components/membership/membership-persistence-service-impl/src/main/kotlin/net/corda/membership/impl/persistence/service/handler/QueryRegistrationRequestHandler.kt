package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryRegistrationRequest
import net.corda.data.membership.db.response.query.RegistrationRequestQueryResponse
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.virtualnode.toCorda

internal class QueryRegistrationRequestHandler(persistenceHandlerServices: PersistenceHandlerServices)
    :BaseRequestStatusHandler<QueryRegistrationRequest, RegistrationRequestQueryResponse>(persistenceHandlerServices)
{
    override fun invoke(
        context: MembershipRequestContext,
        request: QueryRegistrationRequest,
    ): RegistrationRequestQueryResponse {
        val shortHash = context.holdingIdentity.toCorda().shortHash
        return transaction(shortHash) { em ->
            val criteriaBuilder = em.criteriaBuilder
            val queryBuilder = criteriaBuilder.createQuery(RegistrationRequestEntity::class.java)
            val root = queryBuilder.from(RegistrationRequestEntity::class.java)
            val query = queryBuilder
                .select(root)
                .where(criteriaBuilder.equal(root.get<String>("registrationId"), request.registrationRequestId))
            val details =
                em.createQuery(query)
                    .resultList
                    .firstOrNull()
                    ?.toDetails()
            RegistrationRequestQueryResponse(details)
        }
    }

}
