package net.corda.membership.lib.approval

import net.corda.membership.lib.toMap
import net.corda.membership.lib.toWire
import net.corda.v5.membership.MemberInfo

/**
 * Represents a rules engine, which uses [RegistrationRule] rules to determine whether a registration request
 * requires manual approval.
 */
interface RegistrationRulesEngine {
    val rules: Collection<RegistrationRule>

    fun requiresManualApproval(proposedMemberInfo: MemberInfo, activeMemberInfo: MemberInfo?): Boolean

    class Impl(override val rules: Collection<RegistrationRule>) : RegistrationRulesEngine {

        override fun requiresManualApproval(proposedMemberInfo: MemberInfo, activeMemberInfo: MemberInfo?): Boolean {
            val proposedMemberInfoMap = proposedMemberInfo.memberProvidedContext.toWire().toMap()
            val memberInfoDiff = activeMemberInfo?.let {
                proposedMemberInfoMap.keys - it.memberProvidedContext.toWire().toMap().keys
            } ?: proposedMemberInfoMap.keys

            return rules.any {
                it.evaluate(memberInfoDiff)
            }
        }
    }
}
