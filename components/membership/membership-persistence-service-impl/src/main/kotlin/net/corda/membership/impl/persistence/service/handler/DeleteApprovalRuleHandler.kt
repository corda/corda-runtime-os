package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.DeleteApprovalRule
import net.corda.data.membership.db.response.command.DeleteApprovalRuleResponse
import net.corda.membership.datamodel.ApprovalRulesEntity
import net.corda.membership.datamodel.ApprovalRulesEntityPrimaryKey
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.virtualnode.toCorda

internal class DeleteApprovalRuleHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<DeleteApprovalRule, DeleteApprovalRuleResponse>(persistenceHandlerServices) {
    override val operation = DeleteApprovalRule::class.java
    override fun invoke(context: MembershipRequestContext, request: DeleteApprovalRule): DeleteApprovalRuleResponse {
        logger.info("Deleting approval rule.")
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val rule = em.find(
                ApprovalRulesEntity::class.java,
                ApprovalRulesEntityPrimaryKey(
                    request.ruleId,
                    request.ruleType.name
                )
            ) ?: throw MembershipPersistenceException("Approval rule with ID '${request.ruleId}' does not exist.")
            em.remove(rule)
            DeleteApprovalRuleResponse()
        }
    }
}
