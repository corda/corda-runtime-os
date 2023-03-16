package net.corda.membership.rest.v1.types.request

/**
 * Parameters for adding a new approval rule.
 *
 * @param ruleRegex The regular expression associated with the rule to be added.
 * @param ruleLabel Optional. A label describing the rule to be added.
 */
data class ApprovalRuleRequestParams(
    val ruleRegex: String,
    val ruleLabel: String? = null,
)
