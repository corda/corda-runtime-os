package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryRegistrationRequests
import net.corda.data.membership.db.response.query.RegistrationRequestsQueryResponse
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.lib.Ticker
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import java.time.Instant
import javax.persistence.criteria.Predicate

internal class QueryRegistrationRequestsHandler(persistenceHandlerServices: PersistenceHandlerServices)
    :BaseRequestStatusHandler<QueryRegistrationRequests, RegistrationRequestsQueryResponse>(persistenceHandlerServices)
{
    override val operation = QueryRegistrationRequests::class.java
    override fun invoke(
        context: MembershipRequestContext,
        request: QueryRegistrationRequests,
    ): RegistrationRequestsQueryResponse {
        logger.info("Retrieving registration requests.")
        Ticker.tick("invoke 1")
        val requestSubject = request.requestSubjectX500Name?.let {
            HoldingIdentity(MemberX500Name.parse(it), context.holdingIdentity.groupId).shortHash
        }
        val shortHash = context.holdingIdentity.toCorda().shortHash
        Ticker.tick("invoke 2")
        return transaction(shortHash) { em ->
            Ticker.tick("invoke 3")

            val criteriaBuilder = em.criteriaBuilder
            val queryBuilder = criteriaBuilder.createQuery(RegistrationRequestEntity::class.java)
            val root = queryBuilder.from(RegistrationRequestEntity::class.java)
            val predicates = mutableListOf<Predicate>()
            requestSubject?.let {
                predicates.add(
                    em.criteriaBuilder.equal(
                        root.get<String>(RegistrationRequestEntity::holdingIdentityShortHash.name),
                        it.value
                    )
                )
            }
            Ticker.tick("invoke 4")
            request.statuses.let {
                val inStatus = em.criteriaBuilder.`in`(root.get<String>(RegistrationRequestEntity::status.name))
                it.forEach { status ->
                    inStatus.value(status.name)
                }
                predicates.add(inStatus)
            }
            Ticker.tick("invoke 5")
            @Suppress("SpreadOperator")
            val query = queryBuilder
                .select(root)
                .where(*predicates.toTypedArray())
                .orderBy(criteriaBuilder.asc(root.get<Instant>("created")))
            Ticker.tick("invoke 6")
            val details = with(em.createQuery(query).resultList) {
                Ticker.tick("invoke 7")
                if (request.limit != null && isNotEmpty()) {
                    subList(0, request.limit)
                } else {
                    this
                }.map { it.toDetails() }
            }
            Ticker.tick("invoke 8")
            RegistrationRequestsQueryResponse(details)
        }.also {
            Ticker.tick("invoke 9")
        }
    }

}
