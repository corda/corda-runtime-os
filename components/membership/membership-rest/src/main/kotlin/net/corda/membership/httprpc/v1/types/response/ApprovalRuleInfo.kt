package net.corda.membership.httprpc.v1.types.response

/**
 * Data class describing an approval rule.
 */
data class ApprovalRuleInfo(
    val ruleId: String,
    val ruleRegex: String,
    val ruleLabel: String?,
)
