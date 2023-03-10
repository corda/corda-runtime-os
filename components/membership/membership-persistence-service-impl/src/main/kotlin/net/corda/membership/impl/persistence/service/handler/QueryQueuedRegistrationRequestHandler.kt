package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryQueuedRegistrationRequests
import net.corda.data.membership.db.response.query.RegistrationRequestQueryResponse
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.virtualnode.toCorda
import java.time.Instant

internal class QueryQueuedRegistrationRequestHandler(persistenceHandlerServices: PersistenceHandlerServices)
    : BaseRequestStatusHandler<QueryQueuedRegistrationRequests, RegistrationRequestQueryResponse>(persistenceHandlerServices) {
    override fun invoke(
        context: MembershipRequestContext,
        request: QueryQueuedRegistrationRequests,
    ): RegistrationRequestQueryResponse {
        val viewOwnersShortHash = context.holdingIdentity.toCorda().shortHash
        logger.info("Querying for queued registration requests belonging to holding identity with " +
                "ID `${request.requestSubjectShortHash}`.")
        return transaction(viewOwnersShortHash) { em ->
            val criteriaBuilder = em.criteriaBuilder
            val queryBuilder = criteriaBuilder.createQuery(RegistrationRequestEntity::class.java)
            val root = queryBuilder.from(RegistrationRequestEntity::class.java)
            val query = queryBuilder
                .select(root)
                .where(
                    criteriaBuilder.and(
                        criteriaBuilder.equal(root.get<String>("status"), RegistrationStatus.NEW.name),
                        criteriaBuilder.equal(root.get<String>("holdingIdentityShortHash"), request.requestSubjectShortHash)
                    )
                )
                .orderBy(criteriaBuilder.asc(root.get<Instant>("created")))

            val requests = em.createQuery(query).resultList
            if(requests.isNotEmpty()) {
                val oldest = requests.first().toDetails()
                RegistrationRequestQueryResponse(oldest)
            } else {
                RegistrationRequestQueryResponse(null)
            }
        }
    }

}