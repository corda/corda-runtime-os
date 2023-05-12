package net.corda.membership.db.lib

import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.membership.datamodel.ApprovalRulesEntity
import javax.persistence.EntityManager

class QueryApprovalRulesService {

    fun get(
        em: EntityManager,
        ruleType: ApprovalRuleType,
    ): Collection<ApprovalRuleDetails> {
        val queryBuilder = em.criteriaBuilder.createQuery(ApprovalRulesEntity::class.java)
        val root = queryBuilder.from(ApprovalRulesEntity::class.java)
        val query = queryBuilder
            .select(root)
            .where(em.criteriaBuilder.equal(root.get<String>("ruleType"), ruleType.name))

        return em.createQuery(query)
            .resultList
            .map {
                ApprovalRuleDetails(it.ruleId, it.ruleRegex, it.ruleLabel)
            }
    }
}
