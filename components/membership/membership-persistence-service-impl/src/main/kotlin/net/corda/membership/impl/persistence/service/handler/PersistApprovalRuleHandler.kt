package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistApprovalRule
import net.corda.data.membership.db.response.command.PersistApprovalRuleResponse
import net.corda.membership.datamodel.ApprovalRulesEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.virtualnode.toCorda

internal class PersistApprovalRuleHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistApprovalRule, PersistApprovalRuleResponse>(persistenceHandlerServices) {
    override fun invoke(context: MembershipRequestContext, request: PersistApprovalRule): PersistApprovalRuleResponse {
        logger.info("Persisting approval rule.")
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val queryBuilder = em.criteriaBuilder.createQuery(ApprovalRulesEntity::class.java)
            val root = queryBuilder.from(ApprovalRulesEntity::class.java)
            val query = queryBuilder
                .select(root)
                .where(
                    em.criteriaBuilder.and(
                        em.criteriaBuilder.equal(root.get<String>("ruleRegex"), request.rule),
                        em.criteriaBuilder.equal(root.get<String>("ruleType"), request.ruleType.name)
                    )
                )
            if (em.createQuery(query).resultList.isNotEmpty()) {
                logger.warn("Approval rule not added as an identical rule already exists.")
                throw MembershipPersistenceException("Approval rule not added as an identical rule already exists.")
            }
            val ruleId = request.ruleId
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
