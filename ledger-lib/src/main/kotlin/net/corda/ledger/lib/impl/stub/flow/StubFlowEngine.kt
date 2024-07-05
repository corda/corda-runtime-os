package net.corda.ledger.lib.impl.stub.flow

import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.types.MemberX500Name
import java.util.*

class StubFlowEngine : FlowEngine {
    override fun getFlowContextProperties(): FlowContextProperties {
        return StubFlowContextProperties()
    }

    override fun getFlowId(): UUID {
        TODO("Not yet implemented")
    }

    override fun getVirtualNodeName(): MemberX500Name {
        TODO("Not yet implemented")
    }

    override fun <R : Any?> subFlow(subFlow: SubFlow<R>): R {
        TODO("Not yet implemented")
    }
}