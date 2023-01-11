package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistApprovalRule
import net.corda.data.membership.db.response.command.PersistApprovalRuleResponse
import net.corda.membership.datamodel.ApprovalRulesEntity
import net.corda.virtualnode.toCorda
import java.util.UUID

internal class PersistApprovalRuleHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistApprovalRule, PersistApprovalRuleResponse>(persistenceHandlerServices) {
    override fun invoke(context: MembershipRequestContext, request: PersistApprovalRule): PersistApprovalRuleResponse {
        logger.info("Persisting approval rule.")
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val ruleId = UUID.randomUUID().toString()
            em.persist(
                ApprovalRulesEntity(
                    ruleId,
                    request.rule,
                    request.ruleType.name,
                    request.label
                )
            )
            PersistApprovalRuleResponse(ruleId)
        }
    }
}
