package net.corda.membership.lib.approval

/**
 * Represents a rules engine, which uses [RegistrationRule] rules to determine whether a registration request
 * requires manual approval.
 */
interface RegistrationRulesEngine {
    val rules: Collection<RegistrationRule>

    fun requiresManualApproval(proposedMemberInfo: Map<String, String?>, activeMemberInfo: Map<String, String?>?):
            Boolean

    class Impl(override val rules: Collection<RegistrationRule>) : RegistrationRulesEngine {

        override fun requiresManualApproval(
            proposedMemberInfo: Map<String, String?>,
            activeMemberInfo: Map<String, String?>?
        ): Boolean {
            val memberInfoDiff = activeMemberInfo?.let { active ->
                (proposedMemberInfo.entries - active.entries).mapTo(mutableSetOf()) { it.key }
            } ?: proposedMemberInfo.keys

            return rules.any {
                it.evaluate(memberInfoDiff)
            }
        }
    }
}
