package net.corda.membership.httprpc.v1.types.request

/**
 * Parameters for adding a new approval rule.
 *
 * @param ruleRegex The regular expression associated with the rule to be added.
 * @param ruleLabel Optional. A label describing the rule to be added.
 */
data class ApprovalRuleParams(
    val ruleRegex: String,
    val ruleLabel: String? = null,
)
