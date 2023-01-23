package net.corda.membership.lib.impl.approval

import net.corda.membership.lib.approval.RegistrationRule
import net.corda.membership.lib.approval.RegistrationRulesEngine
import net.corda.membership.lib.toMap
import net.corda.membership.lib.toWire
import net.corda.v5.membership.MemberInfo

class RegistrationRulesEngineImpl(
    override val rules: Collection<RegistrationRule>
) : RegistrationRulesEngine {

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
