package net.corda.membership.lib.approval

/**
 * Represents a registration rule, which is applied to registration requests to determine whether a request
 * requires manual approval.
 */
interface RegistrationRule {
    val ruleRegex: Regex

    fun evaluate(memberInfoKeys: Collection<String>): Boolean

    class Impl(override val ruleRegex: Regex) : RegistrationRule {

        override fun evaluate(memberInfoKeys: Collection<String>) =
            memberInfoKeys.any {
                ruleRegex.containsMatchIn(it)
            }
    }
}
