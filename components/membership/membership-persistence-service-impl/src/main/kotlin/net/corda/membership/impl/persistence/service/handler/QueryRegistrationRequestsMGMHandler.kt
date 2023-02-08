package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryRegistrationRequestsMGM
import net.corda.data.membership.db.response.query.RegistrationRequestsQueryResponse
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.virtualnode.toCorda
import javax.persistence.criteria.Predicate

internal class QueryRegistrationRequestsMGMHandler(
    val persistenceHandlerServices: PersistenceHandlerServices
) : BaseRequestStatusHandler<QueryRegistrationRequestsMGM, RegistrationRequestsQueryResponse>(persistenceHandlerServices) {
    override fun invoke(context: MembershipRequestContext, request: QueryRegistrationRequestsMGM): RegistrationRequestsQueryResponse {
        logger.info("Retrieving registration requests.")
        val requestingMember = persistenceHandlerServices.virtualNodeInfoReadService.getAll()
            .firstOrNull { it.holdingIdentity.x500Name.toString() == request.requestingMemberX500Name }
            ?.holdingIdentity?.shortHash
        val statuses = if (request.viewHistoric) {
            RegistrationStatus.values().toList()
        } else {
            RegistrationStatus.values().toList() - listOf(RegistrationStatus.APPROVED, RegistrationStatus.DECLINED)
        }

        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val queryBuilder = em.criteriaBuilder.createQuery(RegistrationRequestEntity::class.java)
            val root = queryBuilder.from(RegistrationRequestEntity::class.java)
            val predicates = mutableListOf<Predicate>()
            requestingMember?.let {
                predicates.add(
                    em.criteriaBuilder.equal(
                        root.get<String>(RegistrationRequestEntity::holdingIdentityShortHash.name),
                        it.value
                    )
                )
            }
            statuses.let {
                val inStatus = em.criteriaBuilder.`in`(root.get<String>(RegistrationRequestEntity::status.name))
                it.forEach { status ->
                    inStatus.value(status.name)
                }
                predicates.add(inStatus)
            }
            @Suppress("SpreadOperator")
            val query = queryBuilder
                .select(root)
                .where(*predicates.toTypedArray())
                .groupBy(root.get<String>(RegistrationRequestEntity::holdingIdentityShortHash.name))

            RegistrationRequestsQueryResponse(
                em.createQuery(query)
                    .resultList
                    .map { it.toDetails() }
            )
        }
    }
}
