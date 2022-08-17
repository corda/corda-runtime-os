package net.cordapp.flowworker.development.testflows

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.types.MemberX500Name

class TestGetNodeNameSubFlow : SubFlow<MemberX500Name> {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    override fun call(): MemberX500Name {
        return flowEngine.virtualNodeName
    }
}