package net.corda.membership.lib.approval

/**
 * Represents a rules engine, which uses [RegistrationRule] rules to determine whether a registration request
 * requires manual approval.
 */
interface RegistrationRulesEngine {
    val rules: Collection<RegistrationRule>

    fun requiresManualApproval(proposedMemberContext: Map<String, String?>, activeMemberContext: Map<String, String?>?):
            Boolean

    class Impl(override val rules: Collection<RegistrationRule>) : RegistrationRulesEngine {

        override fun requiresManualApproval(
            proposedMemberContext: Map<String, String?>,
            activeMemberContext: Map<String, String?>?
        ): Boolean {
            val memberInfoDiff = activeMemberContext?.let { active ->
                ((proposedMemberContext.entries - active.entries) + (active.entries - proposedMemberContext.entries))
                    .mapTo(mutableSetOf()) { it.key }
            } ?: proposedMemberContext.keys

            return rules.any {
                it.evaluate(memberInfoDiff)
            }
        }
    }
}
