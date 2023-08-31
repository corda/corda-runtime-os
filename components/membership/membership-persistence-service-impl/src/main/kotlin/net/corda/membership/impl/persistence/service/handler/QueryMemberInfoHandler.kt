package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryMemberInfo
import net.corda.data.membership.db.response.query.MemberInfoQueryResponse
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.virtualnode.toCorda
import javax.persistence.criteria.Predicate

internal class QueryMemberInfoHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<QueryMemberInfo, MemberInfoQueryResponse>(persistenceHandlerServices) {
    override val operation = QueryMemberInfo::class.java

    @Suppress("SpreadOperator")
    override fun invoke(context: MembershipRequestContext, request: QueryMemberInfo): MemberInfoQueryResponse {
        return MemberInfoQueryResponse(
            transaction(context.holdingIdentity.toCorda().shortHash) { em ->
                val criteriaBuilder = em.criteriaBuilder
                val memberQueryBuilder = criteriaBuilder.createQuery(MemberInfoEntity::class.java)
                val root = memberQueryBuilder.from(MemberInfoEntity::class.java)
                val predicates = mutableListOf<Predicate>()
                if (request.queryIdentities.isNotEmpty()) {
                    logger.info("Querying MemberInfo(s) by name.")
                    val inStatus = criteriaBuilder.`in`(root.get<String>("memberX500Name"))
                    request.queryIdentities.forEach { queryIdentity ->
                        inStatus.value(queryIdentity.x500Name)
                    }
                    predicates.add(inStatus)
                }
                if (!request.queryStatuses.isNullOrEmpty()) {
                    logger.info("Querying MemberInfo(s) by status.")
                    val inStatus = criteriaBuilder.`in`(root.get<String>("status"))
                    request.queryStatuses.forEach { queryStatus ->
                        inStatus.value(queryStatus.toString())
                    }
                    predicates.add(inStatus)
                }
                predicates.add(criteriaBuilder.equal(root.get<Boolean>("isDeleted"), false))
                em.createQuery(
                    memberQueryBuilder.select(root).where(*predicates.toTypedArray())
                ).resultList.map {
                    it.toPersistentMemberInfo(context.holdingIdentity)
                }
            }
        )
    }

    private fun MemberInfoEntity.toPersistentMemberInfo(viewOwningMember: net.corda.data.identity.HoldingIdentity) =
        memberInfoFactory.createPersistentMemberInfo(
            viewOwningMember,
            memberContext,
            mgmContext,
            memberSignatureKey,
            memberSignatureContent,
            memberSignatureSpec,
        )
}
