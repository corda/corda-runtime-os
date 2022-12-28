package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryApprovalRules
import net.corda.data.membership.db.response.query.ApprovalRulesQueryResponse
import net.corda.membership.datamodel.ApprovalRulesEntity
import net.corda.virtualnode.toCorda

internal class QueryApprovalRulesHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<QueryApprovalRules, ApprovalRulesQueryResponse>(persistenceHandlerServices) {
    override fun invoke(context: MembershipRequestContext, request: QueryApprovalRules): ApprovalRulesQueryResponse {
        logger.info("Retrieving approval rules.")
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val queryBuilder = em.criteriaBuilder.createQuery(ApprovalRulesEntity::class.java)
            val root = queryBuilder.from(ApprovalRulesEntity::class.java)
            val query = queryBuilder
                .select(root)
                .where(em.criteriaBuilder.equal(root.get<String>("ruleType"), request.ruleType))

            val approvalRules = em.createQuery(query)
                .resultList
                .map { it.ruleRegex }

            ApprovalRulesQueryResponse(approvalRules)
        }
    }
}
