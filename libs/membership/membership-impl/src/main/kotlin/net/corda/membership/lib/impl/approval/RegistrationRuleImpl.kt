package net.corda.membership.lib.impl.approval

import net.corda.membership.lib.approval.RegistrationRule

class RegistrationRuleImpl(
    override val ruleRegex: Regex
) : RegistrationRule {

    override fun evaluate(memberInfoKeys: Collection<String>) =
        memberInfoKeys.any {
            ruleRegex.containsMatchIn(it)
        }
}
