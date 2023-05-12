package net.corda.membership.db.lib

import net.corda.data.membership.preauth.PreAuthToken
import net.corda.data.membership.preauth.PreAuthTokenStatus
import net.corda.membership.datamodel.PreAuthTokenEntity
import javax.persistence.EntityManager
import javax.persistence.criteria.Predicate

class QueryPreAuthTokenService {
    fun query(
        em: EntityManager,
        tokenId: String?,
        ownerX500Name: String?,
        statuses: Collection<PreAuthTokenStatus>?,
    ): Collection<PreAuthToken> {
        val criteriaBuilder = em.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(PreAuthTokenEntity::class.java)
        val root = criteriaQuery.from(PreAuthTokenEntity::class.java)

        val predicates = mutableListOf<Predicate>()
        tokenId?.let { predicates.add(criteriaBuilder.equal(root.get<String>("tokenId"), it)) }
        statuses?.let { requestPreAuthTokenStatuses ->
            val inStatus = criteriaBuilder.`in`(root.get<String>("status"))
            requestPreAuthTokenStatuses.forEach { requestPreAuthTokenStatus ->
                inStatus.value(requestPreAuthTokenStatus.toString())
            }
            predicates.add(inStatus)
        }
        ownerX500Name?.let { predicates.add(criteriaBuilder.equal(root.get<String>("ownerX500Name"), it)) }

        @Suppress("SpreadOperator")
        val query = criteriaQuery.select(root).where(*predicates.toTypedArray())

        return em.createQuery(query)
            .resultList
            .map {
                it.toAvro()
            }
    }
}
