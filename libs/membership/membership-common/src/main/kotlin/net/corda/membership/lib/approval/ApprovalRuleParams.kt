package net.corda.membership.lib.approval

import net.corda.data.membership.common.ApprovalRuleType

/**
 * Parameters for adding a new approval rule.
 *
 * @param ruleRegex The regular expression associated with the rule to be added.
 * @param ruleType The approval rule type for this rule.
 * @param ruleLabel Optional. A label describing the rule to be added.
 */
data class ApprovalRuleParams(
    val ruleRegex: String,
    val ruleType: ApprovalRuleType,
    val ruleLabel: String? = null,
)