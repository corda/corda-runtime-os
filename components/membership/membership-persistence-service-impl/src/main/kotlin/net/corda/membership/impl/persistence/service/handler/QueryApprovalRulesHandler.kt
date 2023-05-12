package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryApprovalRules
import net.corda.data.membership.db.response.query.ApprovalRulesQueryResponse
import net.corda.membership.db.lib.QueryApprovalRulesService
import net.corda.virtualnode.toCorda

internal class QueryApprovalRulesHandler(
    persistenceHandlerServices: PersistenceHandlerServices,
) : BasePersistenceHandler<QueryApprovalRules, ApprovalRulesQueryResponse>(persistenceHandlerServices) {
    private val queryApprovalRulesService = QueryApprovalRulesService()
    override fun invoke(context: MembershipRequestContext, request: QueryApprovalRules): ApprovalRulesQueryResponse {
        logger.info("Retrieving approval rules.")
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val approvalRules = queryApprovalRulesService.get(
                em,
                request.ruleType,
            )

            ApprovalRulesQueryResponse(approvalRules.toList())
        }
    }
}
