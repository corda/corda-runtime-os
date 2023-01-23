package net.corda.membership.lib.approval

import net.corda.v5.membership.MemberInfo

/**
 * Represents a rules engine, which uses [RegistrationRule] rules to determine whether a registration request
 * requires manual approval.
 */
interface RegistrationRulesEngine {
    val rules: Collection<RegistrationRule>

    fun requiresManualApproval(proposedMemberInfo: MemberInfo, activeMemberInfo: MemberInfo?): Boolean
}
