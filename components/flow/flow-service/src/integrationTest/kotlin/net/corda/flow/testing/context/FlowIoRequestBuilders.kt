package net.corda.flow.testing.context

import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.v5.base.types.MemberX500Name

fun initiateFlowMessageFor(member: MemberX500Name, sessionId: String) =
    FlowIORequest.InitiateFlow(member, sessionId, emptyKeyValuePairList(), emptyKeyValuePairList())
