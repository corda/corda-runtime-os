package net.cordapp.demo.obligation

import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.membership.MemberInfo

fun FlowMessaging.initiateFlow(member: MemberInfo): FlowSession {
    return initiateFlow(member.name)
}

fun FlowMessaging.initiateFlows(members: Iterable<MemberInfo>): Set<FlowSession> {
    return members.map { initiateFlow(it) }.toSet()
}

fun FlowMessaging.initiateFlows(vararg members: MemberInfo): Set<FlowSession> {
    return initiateFlows(members.toList())
}
