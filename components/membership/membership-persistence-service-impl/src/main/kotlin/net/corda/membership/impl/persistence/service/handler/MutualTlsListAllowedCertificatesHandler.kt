package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.MutualTlsListAllowedCertificates
import net.corda.data.membership.db.response.query.MutualTlsListAllowedCertificatesResponse
import net.corda.membership.datamodel.MutualTlsAllowedClientCertificateEntity
import net.corda.virtualnode.toCorda

internal class MutualTlsListAllowedCertificatesHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<MutualTlsListAllowedCertificates, MutualTlsListAllowedCertificatesResponse>(persistenceHandlerServices) {
    override fun invoke(
        context: MembershipRequestContext,
        request: MutualTlsListAllowedCertificates
    ): MutualTlsListAllowedCertificatesResponse {
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val criteriaBuilder = em.criteriaBuilder
            val queryBuilder = criteriaBuilder.createQuery(MutualTlsAllowedClientCertificateEntity::class.java)
            val root = queryBuilder.from(MutualTlsAllowedClientCertificateEntity::class.java)
            val query = queryBuilder
                .select(root)
                .orderBy(criteriaBuilder.asc(root.get<String>("subject")))
            val subjects = em.createQuery(query)
                .resultList
                .map {
                    it.subject
                }
            MutualTlsListAllowedCertificatesResponse(subjects)
        }
    }
}
